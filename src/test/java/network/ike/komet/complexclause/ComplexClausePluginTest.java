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
package network.ike.komet.complexclause;

import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.util.uuid.UuidT5Generator;
import dev.ikm.tinkar.component.Component;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.EntityProxy;
import network.ike.komet.complexclause.bootstrap.ComplexClauseBootstrap;
import network.ike.komet.complexclause.cql.ClauseToCqlProjector;
import network.ike.komet.complexclause.eval.ClauseEvaluator;
import network.ike.komet.complexclause.eval.EvaluationContext;
import network.ike.komet.complexclause.eval.Verdict;
import network.ike.komet.complexclause.model.Clause;
import network.ike.komet.complexclause.model.ClauseAdaptor;
import network.ike.komet.complexclause.model.ClauseExpression;
import network.ike.komet.complexclause.model.ClauseExpressionBuilder;
import network.ike.komet.complexclause.model.ClauseSemantic;
import network.ike.komet.complexclause.terms.ComplexClauseTerms;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates the complex-clause model, vocabulary bootstrap, CQL projection, and native three-valued
 * evaluation against an in-memory Tinkar datastore.
 */
@Disabled("""
        Pre-existing plugin-IT service-discovery gap (not app code): the @BeforeAll startDatastore \
        throws "No controller found with name: Load Ephemeral Store" under surefire's classpath \
        runner. PrimitiveData.selectControllerByName resolves DataServiceController via plain \
        ServiceLoader, which does not see the ephemeral controller the way tinkar's custom runtime \
        loader does. Every @Test here depends on that datastore, so the whole class is skipped. \
        Re-enable once the plugin test harness registers the ephemeral controller for the classpath \
        runner.""")
class ComplexClausePluginTest {

    @BeforeAll
    static void startDatastore() {
        // Classpath test mode (ike-parent's surefire default): the ephemeral DataServiceController and
        // the ExecutorController are registered via test-resource META-INF/services. CachingService is
        // deliberately NOT registered — clearAll() would otherwise drive ExecutorProvider's reset(),
        // whose PluggableService.first(Controller.class) only resolves under tinkar's custom runtime
        // loader, not plain ServiceLoader.
        PrimitiveData.selectControllerByName("Load Ephemeral Store");
        PrimitiveData.start();
    }

    @AfterAll
    static void stopDatastore() {
        PrimitiveData.stop();
    }

    private static ConceptFacade concept(String name) {
        return EntityProxy.Concept.make(name, UuidT5Generator.get(ComplexClauseTerms.NAMESPACE, "test:" + name));
    }

    // ---- Bootstrap idempotency -------------------------------------------------------------------

    @Test
    void bootstrapIsIdempotent() {
        ComplexClauseBootstrap.ensureBootstrapped();
        assertTrue(ComplexClauseBootstrap.isBootstrapped(), "pattern present after bootstrap");
        assertFalse(ComplexClauseBootstrap.ensureBootstrapped(), "second bootstrap is a no-op");
        assertTrue(EntityService.get()
                        .getEntity((Component) ComplexClauseTerms.COMPLEX_CONCEPT_CLAUSE_PATTERN).isPresent(),
                "pattern entity is in the store");
    }

    // ---- Builder -> DiTree -> read-back round-trip -----------------------------------------------

    @Test
    void clauseRoundTripsThroughTheGraph() {
        ClauseExpressionBuilder builder = new ClauseExpressionBuilder();
        Clause comparison = builder.GreaterOrEqual(
                builder.Property("BMI determination", "value"), builder.Quantity(40, "kg/m2"));
        builder.setExpression(builder.Or(comparison));

        ClauseExpression clause = ClauseExpression.from(builder.asDiTree());

        Clause top = clause.expression();
        assertInstanceOf(ClauseAdaptor.NaryConnectiveAdaptor.class, top);
        assertEquals(ClauseSemantic.OR, top.clauseSemantic());

        Clause onlyChild = top.children().get(0);
        assertInstanceOf(ClauseAdaptor.ComparisonAdaptor.class, onlyChild);
        assertEquals(ClauseSemantic.GREATER_OR_EQUAL, onlyChild.clauseSemantic());

        ClauseAdaptor.ComparisonAdaptor cmp = (ClauseAdaptor.ComparisonAdaptor) onlyChild;
        assertInstanceOf(ClauseAdaptor.PropertyAdaptor.class, cmp.left());
        ClauseAdaptor.PropertyAdaptor property = (ClauseAdaptor.PropertyAdaptor) cmp.left();
        assertEquals("value", property.path());
        assertEquals("BMI determination", property.sourceLabel());

        assertInstanceOf(ClauseAdaptor.LiteralAdaptor.class, cmp.right());
        ClauseAdaptor.LiteralAdaptor quantity = (ClauseAdaptor.LiteralAdaptor) cmp.right();
        assertEquals(ClauseSemantic.QUANTITY, quantity.clauseSemantic());
        assertEquals(40.0f, ((Number) quantity.value()).floatValue());
        assertEquals("kg/m2", quantity.unit());
    }

    // ---- CQL projection golden tests (Layer-1 join + Layer-2 residual) ---------------------------

    @Test
    void projectsMorbidObesityClause() {
        ConceptFacade condition = concept("Condition");
        ConceptFacade morbidObesity = concept("Morbid obesity");
        ConceptFacade comorbidity = concept("Obesity-related comorbidity");

        ClauseExpressionBuilder builder = new ClauseExpressionBuilder();
        Clause codedPath = builder.Exists(builder.Retrieve(condition, morbidObesity));
        Clause bmi40 = builder.GreaterOrEqual(
                builder.Property("BMI determination", "value"), builder.Quantity(40, "kg/m2"));
        Clause conditional = builder.And(
                builder.GreaterOrEqual(builder.Property("BMI determination", "value"), builder.Quantity(35, "kg/m2")),
                builder.Exists(builder.Retrieve(condition, comorbidity)));
        builder.setExpression(builder.Or(codedPath, bmi40, conditional));

        String cql = new ClauseToCqlProjector(layer1 -> "<< " + layer1.description())
                .project(builder.build(), "Has morbid obesity");

        // Layer 1 — value sets from the referenced classes (the inferred-EL++ join).
        assertTrue(cql.contains("valueset \"Morbid obesity\" = ECL{ << Morbid obesity }"), cql);
        assertTrue(cql.contains("valueset \"Obesity-related comorbidity\" = ECL{ << Obesity-related comorbidity }"), cql);
        // Layer 2 — residual logic.
        assertTrue(cql.contains("define \"Has morbid obesity\":"), cql);
        assertTrue(cql.contains("exists [Condition: \"Morbid obesity\"]"), cql);
        assertTrue(cql.contains("\"BMI determination\".value >= 40 'kg/m2'"), cql);
        assertTrue(cql.contains("\"BMI determination\".value >= 35 'kg/m2'"), cql);
        assertTrue(cql.contains("and exists [Condition: \"Obesity-related comorbidity\"]"), cql);
    }

    @Test
    void projectsHer2TkiClauseWithNegationAndThreshold() {
        ConceptFacade molecularObservation = concept("MolecularObservation");
        ConceptFacade activatingErbb2 = concept("Activating ERBB2 variant");
        ConceptFacade genotype = concept("Genotype");
        ConceptFacade t798m = concept("T798M");

        ClauseExpressionBuilder builder = new ClauseExpressionBuilder();
        ClauseAdaptor.RetrieveAdaptor observation = builder.Retrieve(molecularObservation, activatingErbb2, "O", null);
        Clause where = builder.And(
                builder.GreaterOrEqual(builder.Property(observation, "value"), builder.Quantity(2.6, null)),
                builder.Equal(builder.Property(observation, "codonChangeDistance"), builder.IntegerLiteral(1)),
                builder.Not(builder.Exists(builder.Retrieve(genotype, t798m))));
        builder.where(observation, where);
        builder.setExpression(builder.Exists(observation));

        String cql = new ClauseToCqlProjector(layer1 -> "<< " + layer1.description())
                .project(builder.build(), "HER2 TKI candidate");

        assertTrue(cql.contains("valueset \"Activating ERBB2 variant\" = ECL{ << Activating ERBB2 variant }"), cql);
        assertTrue(cql.contains("valueset \"T798M\" = ECL{ << T798M }"), cql);
        assertTrue(cql.contains("define \"HER2 TKI candidate\":"), cql);
        assertTrue(cql.contains("exists [MolecularObservation: \"Activating ERBB2 variant\"] O where"), cql);
        assertTrue(cql.contains("O.value >= 2.6"), cql);
        assertTrue(cql.contains("O.codonChangeDistance = 1"), cql);
        assertTrue(cql.contains("not (exists [Genotype: \"T798M\"])"), cql);
    }

    // ---- Native three-valued evaluation ----------------------------------------------------------

    @Test
    void evaluatesComparisonsThreeValued() {
        ClauseExpressionBuilder builder = new ClauseExpressionBuilder();
        builder.setExpression(builder.GreaterOrEqual(
                builder.Property("BMI determination", "value"), builder.Quantity(40, "kg/m2")));
        ClauseExpression clause = builder.build();
        ClauseEvaluator evaluator = new ClauseEvaluator();

        assertEquals(Verdict.MET, evaluator.evaluate(clause,
                new EvaluationContext(null, null).put("BMI determination.value", 42.0)));
        assertEquals(Verdict.NOT_MET, evaluator.evaluate(clause,
                new EvaluationContext(null, null).put("BMI determination.value", 38.0)));
        assertEquals(Verdict.INDETERMINATE, evaluator.evaluate(clause,
                new EvaluationContext(null, null)));
    }

    @Test
    void evaluatesKleeneConnectives() {
        ClauseEvaluator evaluator = new ClauseEvaluator();

        // Or(indeterminate, true) -> MET
        ClauseExpressionBuilder orBuilder = new ClauseExpressionBuilder();
        Clause indeterminate = orBuilder.Greater(
                orBuilder.Property("missing", "value"), orBuilder.Quantity(1, null));
        orBuilder.setExpression(orBuilder.Or(indeterminate, orBuilder.BooleanLiteral(true)));
        assertEquals(Verdict.MET, evaluator.evaluate(orBuilder.build(), new EvaluationContext(null, null)));

        // And(met, indeterminate) -> INDETERMINATE
        ClauseExpressionBuilder andBuilder = new ClauseExpressionBuilder();
        Clause met = andBuilder.GreaterOrEqual(andBuilder.Property("a", "b"), andBuilder.Quantity(1, null));
        Clause missing = andBuilder.GreaterOrEqual(andBuilder.Property("c", "d"), andBuilder.Quantity(1, null));
        andBuilder.setExpression(andBuilder.And(met, missing));
        assertEquals(Verdict.INDETERMINATE, evaluator.evaluate(andBuilder.build(),
                new EvaluationContext(null, null).put("a.b", 5.0)));

        // Not(met) -> NOT_MET; Not(indeterminate) -> INDETERMINATE
        ClauseExpressionBuilder notBuilder = new ClauseExpressionBuilder();
        Clause comparison = notBuilder.GreaterOrEqual(notBuilder.Property("p", "q"), notBuilder.Quantity(1, null));
        notBuilder.setExpression(notBuilder.Not(comparison));
        ClauseExpression notClause = notBuilder.build();
        assertEquals(Verdict.NOT_MET, evaluator.evaluate(notClause,
                new EvaluationContext(null, null).put("p.q", 5.0)));
        assertEquals(Verdict.INDETERMINATE, evaluator.evaluate(notClause, new EvaluationContext(null, null)));
    }
}
