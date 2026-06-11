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
package network.ike.komet.complexclause.eval;

import dev.ikm.tinkar.terms.ConceptFacade;
import network.ike.komet.complexclause.model.Clause;
import network.ike.komet.complexclause.model.ClauseAdaptor;
import network.ike.komet.complexclause.model.ClauseExpression;
import network.ike.komet.complexclause.model.ClauseSemantic;

import java.util.Optional;

/**
 * Evaluates a complex-concept clause natively over an {@link EvaluationContext} (the open
 * Tinkar/ANF substrate), with Kleene three-valued logic — the same null-as-unknown semantics CQL
 * uses, so the in-app verdict agrees with what the emitted CQL would yield on complete data.
 *
 * <p>v1 covers the operators the obesity/HER2 worked examples need (connectives, comparisons,
 * existence/membership, literals); interval/aggregate evaluation returns
 * {@link Verdict#INDETERMINATE} pending ANF statement traversal.
 */
public final class ClauseEvaluator {

    /**
     * Evaluates a whole clause expression.
     *
     * @param clause  the clause
     * @param context the data context
     * @return the three-valued verdict
     */
    public Verdict evaluate(ClauseExpression clause, EvaluationContext context) {
        Clause top = clause.expression();
        return top == null ? Verdict.INDETERMINATE : evaluate(top, context);
    }

    /**
     * Evaluates a clause node in boolean position.
     *
     * @param clause  the node
     * @param context the data context
     * @return the three-valued verdict
     */
    public Verdict evaluate(Clause clause, EvaluationContext context) {
        return switch (clause) {
            case ClauseAdaptor.RootAdaptor root -> {
                Clause expr = root.expression();
                yield expr == null ? Verdict.INDETERMINATE : evaluate(expr, context);
            }
            case ClauseAdaptor.NaryConnectiveAdaptor connective ->
                    connective.clauseSemantic() == ClauseSemantic.AND
                            ? and(connective, context)
                            : or(connective, context);
            case ClauseAdaptor.NotAdaptor not -> negate(evaluate(not.operand(), context));
            case ClauseAdaptor.ComparisonAdaptor comparison -> compare(comparison, context);
            case ClauseAdaptor.ExistsAdaptor exists -> existence(exists.source(), context);
            case ClauseAdaptor.InAdaptor in -> membership(in.subject(), in.valueSet(), context);
            case ClauseAdaptor.RetrieveAdaptor retrieve -> existence(retrieve, context);
            case ClauseAdaptor.PropertyAdaptor property -> asBoolean(resolveValue(property, context));
            case ClauseAdaptor.LiteralAdaptor literal -> literalBoolean(literal);
            // Non-boolean or not-yet-evaluable nodes in boolean position.
            case ClauseAdaptor.ValueSetRefAdaptor ignored -> Verdict.INDETERMINATE;
            case ClauseAdaptor.AggregateAdaptor ignored -> Verdict.INDETERMINATE;
            case ClauseAdaptor.IntervalOpAdaptor ignored -> Verdict.INDETERMINATE;
        };
    }

    // ---- Connectives (Kleene three-valued) -------------------------------------------------------

    private Verdict and(ClauseAdaptor.NaryConnectiveAdaptor connective, EvaluationContext context) {
        boolean anyIndeterminate = false;
        for (Clause element : connective.elements()) {
            Verdict v = evaluate(element, context);
            if (v == Verdict.NOT_MET) {
                return Verdict.NOT_MET;
            }
            if (v == Verdict.INDETERMINATE) {
                anyIndeterminate = true;
            }
        }
        return anyIndeterminate ? Verdict.INDETERMINATE : Verdict.MET;
    }

    private Verdict or(ClauseAdaptor.NaryConnectiveAdaptor connective, EvaluationContext context) {
        boolean anyIndeterminate = false;
        for (Clause element : connective.elements()) {
            Verdict v = evaluate(element, context);
            if (v == Verdict.MET) {
                return Verdict.MET;
            }
            if (v == Verdict.INDETERMINATE) {
                anyIndeterminate = true;
            }
        }
        return anyIndeterminate ? Verdict.INDETERMINATE : Verdict.NOT_MET;
    }

    private static Verdict negate(Verdict v) {
        return switch (v) {
            case MET -> Verdict.NOT_MET;
            case NOT_MET -> Verdict.MET;
            case INDETERMINATE -> Verdict.INDETERMINATE;
        };
    }

    // ---- Comparisons -----------------------------------------------------------------------------

    private Verdict compare(ClauseAdaptor.ComparisonAdaptor comparison, EvaluationContext context) {
        Double left = resolveNumber(comparison.left(), context);
        Double right = resolveNumber(comparison.right(), context);
        if (left == null || right == null) {
            return Verdict.INDETERMINATE;
        }
        int cmp = Double.compare(left, right);
        boolean result = switch (comparison.clauseSemantic()) {
            case EQUAL -> cmp == 0;
            case GREATER -> cmp > 0;
            case GREATER_OR_EQUAL -> cmp >= 0;
            case LESS -> cmp < 0;
            case LESS_OR_EQUAL -> cmp <= 0;
            default -> throw new IllegalStateException("Not a comparison: " + comparison.clauseSemantic());
        };
        return result ? Verdict.MET : Verdict.NOT_MET;
    }

    // ---- Existence / membership ------------------------------------------------------------------

    private Verdict existence(Clause source, EvaluationContext context) {
        if (source instanceof ClauseAdaptor.RetrieveAdaptor retrieve && retrieve.valueSetConcept() != null
                && context.subject() != null) {
            return context.isMember(context.subject().nid(), retrieve.valueSetConcept().nid())
                    .map(member -> member ? Verdict.MET : Verdict.NOT_MET)
                    .orElse(Verdict.INDETERMINATE);
        }
        return Verdict.INDETERMINATE;
    }

    private Verdict membership(Clause subjectClause, Clause valueSetClause, EvaluationContext context) {
        ConceptFacade subject = subjectConcept(subjectClause, context);
        ConceptFacade valueSetClass = valueSetClause instanceof ClauseAdaptor.ValueSetRefAdaptor ref
                ? ref.valueSetConcept() : null;
        if (subject == null || valueSetClass == null) {
            return Verdict.INDETERMINATE;
        }
        return context.isMember(subject.nid(), valueSetClass.nid())
                .map(member -> member ? Verdict.MET : Verdict.NOT_MET)
                .orElse(Verdict.INDETERMINATE);
    }

    private ConceptFacade subjectConcept(Clause subjectClause, EvaluationContext context) {
        if (subjectClause instanceof ClauseAdaptor.LiteralAdaptor literal
                && literal.clauseSemantic() == ClauseSemantic.CODE) {
            return literal.code();
        }
        return context.subject();
    }

    // ---- Value resolution ------------------------------------------------------------------------

    private Double resolveNumber(Clause clause, EvaluationContext context) {
        return switch (clause) {
            case ClauseAdaptor.LiteralAdaptor literal -> toDouble(literal.value());
            case ClauseAdaptor.PropertyAdaptor property -> toDouble(resolveValue(property, context).orElse(null));
            default -> null;
        };
    }

    private Optional<Object> resolveValue(ClauseAdaptor.PropertyAdaptor property, EvaluationContext context) {
        return context.lookup(EvaluationContext.propertyKey(property.sourceLabel(), property.path()));
    }

    private Verdict literalBoolean(ClauseAdaptor.LiteralAdaptor literal) {
        if (literal.clauseSemantic() == ClauseSemantic.BOOLEAN) {
            return Boolean.TRUE.equals(literal.value()) ? Verdict.MET : Verdict.NOT_MET;
        }
        return Verdict.INDETERMINATE;
    }

    private static Verdict asBoolean(Optional<Object> value) {
        if (value.isEmpty()) {
            return Verdict.INDETERMINATE;
        }
        Object v = value.get();
        if (v instanceof Boolean b) {
            return b ? Verdict.MET : Verdict.NOT_MET;
        }
        return Verdict.INDETERMINATE;
    }

    private static Double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return null;
    }
}
