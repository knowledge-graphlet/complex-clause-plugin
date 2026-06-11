/*
 * Copyright © 2026 Knowledge Graphlet / IKE Network
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package network.ike.komet.complexclause.cql;

import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.coordinate.view.calculator.ViewCalculator;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.TinkarTerm;
import network.ike.komet.complexclause.ClauseStore;
import network.ike.komet.complexclause.model.Clause;
import network.ike.komet.complexclause.model.ClauseAdaptor;
import network.ike.komet.complexclause.model.ClauseExpression;
import network.ike.komet.complexclause.model.ClauseSemantic;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Explicates a complex-concept clause to CQL — the portable, FHIR-applicable projection. The emitted
 * text <strong>joins the inferred EL++ value set (Layer 1) with the Layer-2 residual</strong>: every
 * value-set reference becomes a {@code valueset … = ECL{ << class }} declaration (the class's inferred
 * subsumption set), and the clause tree becomes the {@code define} body. This is string generation
 * only — no CQL engine, no HAPI FHIR.
 */
public final class ClauseToCqlProjector {

    /** Resolves the ECL body (inside {@code ECL{ … }}) for a Layer-1 value-set class. */
    @FunctionalInterface
    public interface ValueSetResolver {
        /**
         * @param layer1Class the EL++ class whose inferred subsumption set is the value set
         * @return the ECL expression, e.g. {@code << Morbid obesity}
         */
        String ecl(ConceptFacade layer1Class);
    }

    private final ValueSetResolver resolver;
    private final ViewCalculator calculator;

    /**
     * @param resolver supplies the ECL for each value-set class (the Layer-1 join)
     */
    public ClauseToCqlProjector(ValueSetResolver resolver) {
        this(resolver, null);
    }

    /**
     * @param resolver   supplies the ECL for each value-set class
     * @param calculator optional view for resolving concept display names
     */
    public ClauseToCqlProjector(ValueSetResolver resolver, ViewCalculator calculator) {
        this.resolver = resolver;
        this.calculator = calculator;
    }

    /**
     * Projects a clause to a CQL library fragment (value-set declarations + one {@code define}).
     *
     * @param clause     the clause
     * @param defineName the name of the emitted {@code define}
     * @return CQL text
     */
    public String project(ClauseExpression clause, String defineName) {
        Map<String, String> valueSets = new LinkedHashMap<>();
        Clause top = clause.expression();
        String body = top == null ? "true" : translate(top, valueSets);

        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, String> entry : valueSets.entrySet()) {
            out.append("valueset \"").append(entry.getKey()).append("\" = ECL{ ")
                    .append(entry.getValue()).append(" }\n");
        }
        if (!valueSets.isEmpty()) {
            out.append('\n');
        }
        out.append("define \"").append(defineName).append("\":\n  ").append(body).append('\n');
        return out.toString();
    }

    /**
     * Live path: reads a concept's clause semantic, derives value-set ECL from the inferred EL++
     * classification, and projects. Demonstrates the Layer-1 / Layer-2 join end to end.
     *
     * @param concept    the Complex Concept
     * @param calculator the journal view
     * @return CQL text
     */
    public static String project(ConceptFacade concept, ViewCalculator calculator) {
        ClauseExpression clause = ClauseStore.readClause(concept, calculator)
                .orElseThrow(() -> new IllegalStateException(
                        "No complex-concept clause semantic on concept nid " + concept.nid()));
        ClauseToCqlProjector projector =
                new ClauseToCqlProjector(layer1 -> "<< " + projectorName(layer1, calculator), calculator);
        String defineName = projectorName(concept, calculator);
        boolean inferredPresent = EntityService.get().semanticNidsForComponentOfPattern(
                concept.nid(), TinkarTerm.EL_PLUS_PLUS_INFERRED_AXIOMS_PATTERN.nid()).length > 0;
        String header = "";
        if (inferredPresent) {
            header = "// Layer 1 — value set from the inferred EL++ classification of \""
                    + defineName + "\":\n"
                    + "valueset \"" + defineName + "\" = ECL{ << " + defineName + " }\n\n";
        }
        return header + projector.project(clause, defineName);
    }

    // ---- Translation -----------------------------------------------------------------------------

    private String translate(Clause clause, Map<String, String> valueSets) {
        return switch (clause) {
            case ClauseAdaptor.RootAdaptor root -> {
                Clause expression = root.expression();
                yield expression == null ? "true" : translate(expression, valueSets);
            }
            case ClauseAdaptor.NaryConnectiveAdaptor connective -> {
                String operator = connective.clauseSemantic() == ClauseSemantic.AND ? " and " : " or ";
                yield "(" + connective.elements().stream()
                        .map(element -> translate(element, valueSets))
                        .collect(Collectors.joining(operator)) + ")";
            }
            case ClauseAdaptor.NotAdaptor not -> "not (" + translate(not.operand(), valueSets) + ")";
            case ClauseAdaptor.ComparisonAdaptor comparison -> "(" + translate(comparison.left(), valueSets) + " "
                    + comparisonOperator(comparison.clauseSemantic()) + " "
                    + translate(comparison.right(), valueSets) + ")";
            case ClauseAdaptor.ExistsAdaptor exists -> "exists " + translate(exists.source(), valueSets);
            case ClauseAdaptor.InAdaptor in -> "(" + translate(in.subject(), valueSets) + " in "
                    + translate(in.valueSet(), valueSets) + ")";
            case ClauseAdaptor.ValueSetRefAdaptor reference ->
                    "\"" + register(reference.valueSetConcept(), valueSets) + "\"";
            case ClauseAdaptor.RetrieveAdaptor retrieve -> retrieve(retrieve, valueSets);
            case ClauseAdaptor.PropertyAdaptor property -> propertyReference(property, valueSets);
            case ClauseAdaptor.AggregateAdaptor aggregate ->
                    (aggregate.clauseSemantic() == ClauseSemantic.COUNT ? "Count(" : "Sum(")
                            + translate(aggregate.source(), valueSets) + ")";
            case ClauseAdaptor.IntervalOpAdaptor interval -> "(" + translate(interval.left(), valueSets) + " "
                    + intervalOperator(interval.clauseSemantic()) + " "
                    + translate(interval.right(), valueSets) + ")";
            case ClauseAdaptor.LiteralAdaptor literal -> literal(literal);
        };
    }

    private String retrieve(ClauseAdaptor.RetrieveAdaptor retrieve, Map<String, String> valueSets) {
        StringBuilder out = new StringBuilder("[").append(text(retrieve.resourceType()));
        if (retrieve.valueSetConcept() != null) {
            out.append(": \"").append(register(retrieve.valueSetConcept(), valueSets)).append('"');
        }
        out.append(']');
        Clause where = retrieve.where();
        if (where != null) {
            out.append(' ').append(retrieve.alias()).append(" where ").append(translate(where, valueSets));
        }
        return out.toString();
    }

    private String propertyReference(ClauseAdaptor.PropertyAdaptor property, Map<String, String> valueSets) {
        String source;
        if (property.sourceLabel() != null) {
            source = "\"" + property.sourceLabel() + "\"";
        } else if (property.source() instanceof ClauseAdaptor.RetrieveAdaptor retrieve) {
            source = retrieve.alias();
        } else if (property.source() != null) {
            source = translate(property.source(), valueSets);
        } else {
            source = "";
        }
        return source.isEmpty() ? property.path() : source + "." + property.path();
    }

    private String literal(ClauseAdaptor.LiteralAdaptor literal) {
        return switch (literal.clauseSemantic()) {
            case QUANTITY -> {
                String unit = literal.unit();
                yield formatNumber(literal.value()) + (unit != null ? " '" + unit + "'" : "");
            }
            case INTEGER, BOOLEAN -> String.valueOf(literal.value());
            case CODE -> "\"" + text(literal.code()) + "\"";
            default -> throw new IllegalStateException("Not a literal: " + literal.clauseSemantic());
        };
    }

    private String register(ConceptFacade layer1Class, Map<String, String> valueSets) {
        String name = text(layer1Class);
        valueSets.putIfAbsent(name, resolver.ecl(layer1Class));
        return name;
    }

    private static String comparisonOperator(ClauseSemantic semantic) {
        return switch (semantic) {
            case EQUAL -> "=";
            case GREATER -> ">";
            case GREATER_OR_EQUAL -> ">=";
            case LESS -> "<";
            case LESS_OR_EQUAL -> "<=";
            default -> throw new IllegalStateException("Not a comparison: " + semantic);
        };
    }

    private static String intervalOperator(ClauseSemantic semantic) {
        return switch (semantic) {
            case INTERVAL_IN -> "in";
            case INCLUDED_IN -> "included in";
            case OVERLAPS -> "overlaps";
            default -> throw new IllegalStateException("Not an interval operator: " + semantic);
        };
    }

    private static String formatNumber(Object value) {
        if (value instanceof Float floatValue) {
            if (!floatValue.isInfinite() && !floatValue.isNaN() && floatValue == Math.floor(floatValue)) {
                return String.valueOf(floatValue.intValue());
            }
            return floatValue.toString();
        }
        return String.valueOf(value);
    }

    private String text(ConceptFacade concept) {
        return projectorName(concept, calculator);
    }

    private static String projectorName(ConceptFacade concept, ViewCalculator calculator) {
        if (calculator != null) {
            var description = calculator.getDescriptionText(concept.nid());
            if (description.isPresent() && !description.get().isBlank()) {
                return description.get();
            }
        }
        try {
            String description = concept.description();
            if (description != null && !description.isBlank()) {
                return description;
            }
        } catch (RuntimeException ignored) {
            // fall through to primitive text
        }
        String primitive = PrimitiveData.text(concept.nid());
        return primitive != null && !primitive.isBlank() ? primitive : ("nid:" + concept.nid());
    }
}
