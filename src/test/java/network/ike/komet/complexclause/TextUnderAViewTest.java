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

import dev.ikm.komet.terms.KometTerm;
import dev.ikm.tinkar.terms.KernelTerm;
import dev.ikm.tinkar.common.id.LongIds;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.util.uuid.UuidT5Generator;
import dev.ikm.tinkar.coordinate.Calculators;
import dev.ikm.tinkar.coordinate.Coordinates;
import dev.ikm.tinkar.coordinate.language.LanguageCoordinateRecord;
import dev.ikm.tinkar.coordinate.view.ViewCoordinateRecord;
import dev.ikm.tinkar.coordinate.view.calculator.ViewCalculator;
import dev.ikm.tinkar.coordinate.view.calculator.ViewCalculatorWithCache;
import dev.ikm.tinkar.entity.ConceptRecord;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.StampEntity;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.entity.load.LoadEntitiesFromProtobufFile;
import dev.ikm.tinkar.entity.transaction.Transaction;
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.EntityProxy;
import dev.ikm.tinkar.terms.State;
import network.ike.komet.complexclause.bootstrap.ComplexClauseBootstrap;
import network.ike.komet.complexclause.cql.ClauseToCqlProjector;
import network.ike.komet.complexclause.model.Clause;
import network.ike.komet.complexclause.model.ClauseExpressionBuilder;
import network.ike.komet.complexclause.model.ConceptText;
import network.ike.komet.complexclause.terms.ComplexClauseTerms;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The plugin's text under a view, against the IKE starter set in an in-memory store
 * ({@code IKE-Network/ike-issues#1185}).
 *
 * <p>This is how the plugin runs in Komet: the panel always has a view, and the live path reads
 * a concept's clause from the store and explicates it. A concept is named by the description
 * the view selects; when the view selects none, by the store's own description; and when the
 * concept has no description at all, by its UUID. No text holds a nid.
 */
class TextUnderAViewTest {

    private static final File PB_STARTER_DATA = new File("target/data/ike-starter-set-reasoned-pb.zip");

    /** A nid of the in-memory store in decimal: {@code Integer.MIN_VALUE} plus a small count. */
    private static final Pattern STORE_NID = Pattern.compile("-2147[34]\\d{5}(?!\\d)");

    /** The form {@code PrimitiveData.text} writes for a component with no description. */
    private static final Pattern ANGLE_BRACKET_NID = Pattern.compile("<-?\\d+>");

    /** A number labelled as a nid: {@code nid:N}, {@code nid N}, {@code nid=N}. */
    private static final Pattern LABELLED_NID = Pattern.compile("(?i)\\bnid\\b\\s*[=:]?\\s*-?\\d+");

    /** A concept the tests write into the store with no description. */
    private static final UUID UNDESCRIBED =
            UuidT5Generator.get(ComplexClauseTerms.NAMESPACE, "text-under-a-view-test:undescribed concept");

    private static ViewCalculator view;
    private static ViewCalculator viewThatSelectsNoDescription;
    private static long undescribedNid;

    @BeforeAll
    static void startDatastoreWithTheStarterData() {
        assertTrue(PB_STARTER_DATA.exists(),
                "Starter data must be present at " + PB_STARTER_DATA.getAbsolutePath()
                        + " (copied by maven-dependency-plugin in process-test-resources).");
        PrimitiveData.selectControllerByName("Load Ephemeral Store");
        PrimitiveData.start();
        long count = new LoadEntitiesFromProtobufFile(PB_STARTER_DATA).compute().getTotalCount();
        assertTrue(count > 0, "Should load entities from the starter-data protobuf file");

        ComplexClauseBootstrap.ensureBootstrapped();
        view = Calculators.View.Default();
        viewThatSelectsNoDescription = aViewThatSelectsNoDescription();
        undescribedNid = writeConceptWithNoDescription(UNDESCRIBED).nid();
    }

    @AfterAll
    static void stopDatastore() {
        PrimitiveData.stop();
    }

    // ---- The name of a concept -------------------------------------------------------------------

    @Test
    void theNameIsTheDescriptionTheViewSelects() {
        long nid = KernelTerm.ENGLISH_LANGUAGE.nid();
        String selected = view.getDescriptionText(nid).orElseThrow();

        assertEquals(selected, ConceptText.name(KernelTerm.ENGLISH_LANGUAGE, view));
        assertEquals(selected, ConceptText.name(EntityProxy.Concept.make(nid), view),
                "a proxy made from the nid alone, as a stored clause holds it");
    }

    @Test
    void whenTheViewSelectsNoneTheNameIsTheStoresOwnDescription() {
        long nid = KernelTerm.ENGLISH_LANGUAGE.nid();
        assertTrue(viewThatSelectsNoDescription.getDescriptionText(nid).isEmpty(),
                "precondition: the view selects no description for the concept");
        // The calculator method that answers with the nid here; the assertion fails when
        // tinkar-core stops, which is the moment to reconsider ConceptText.
        assertEquals(Long.toString(nid), viewThatSelectsNoDescription.getDescriptionTextOrNid(nid));

        String name = ConceptText.name(EntityProxy.Concept.make(nid), viewThatSelectsNoDescription);

        assertEquals(PrimitiveData.textOptional(nid).orElseThrow(), name);
        assertNoNid("the name", name, nid);
    }

    @Test
    void aConceptWithNoDescriptionIsNamedByItsUuidUnderEveryView() {
        ConceptFacade undescribed = EntityProxy.Concept.make(undescribedNid);
        assertTrue(view.getDescriptionText(undescribedNid).isEmpty(),
                "precondition: the concept has no description");

        for (ViewCalculator calculator : new ViewCalculator[]{view, viewThatSelectsNoDescription, null}) {
            String name = ConceptText.name(undescribed, calculator);
            assertEquals(UNDESCRIBED.toString(), name);
            assertNoNid("the name", name, undescribedNid);
        }
    }

    // ---- The live path: a clause read from the store and explicated ------------------------------

    @Test
    void aStoredClauseIsExplicatedWithTheNamesTheViewSelects() {
        ConceptFacade concept = KernelTerm.ENGLISH_LANGUAGE;
        String name = view.getDescriptionText(concept.nid()).orElseThrow();
        writeTheSampleClauseOn(concept);

        String cql = ClauseToCqlProjector.project(concept, view);

        // The starter data classifies the concept, so the Layer-1 value set comes first.
        assertEquals("// Layer 1 — value set from the inferred EL++ classification of \"" + name + "\":\n"
                        + "valueset \"" + name + "\" = ECL{ << " + name + " }\n"
                        + "\n"
                        + "valueset \"" + name + "\" = ECL{ << " + name + " }\n"
                        + "\n"
                        + "define \"" + name + "\":\n"
                        + "  ((\"" + name + "\" in \"" + name + "\") or (\"BMI determination\".value >= 40 'kg/m2'))\n",
                cql);
        assertNoNid("the CQL", cql, concept.nid());
    }

    @Test
    void aStoredClauseOnAConceptWithNoDescriptionIsExplicatedWithItsUuid() {
        ConceptFacade concept = EntityProxy.Concept.make(PublicIds.of(UNDESCRIBED));
        writeTheSampleClauseOn(concept);

        String cql = ClauseToCqlProjector.project(concept, view);

        assertEquals("valueset \"" + UNDESCRIBED + "\" = ECL{ << " + UNDESCRIBED + " }\n"
                        + "\n"
                        + "define \"" + UNDESCRIBED + "\":\n"
                        + "  ((\"" + UNDESCRIBED + "\" in \"" + UNDESCRIBED
                        + "\") or (\"BMI determination\".value >= 40 'kg/m2'))\n",
                cql);
        assertNoNid("the CQL", cql, undescribedNid);
    }

    // ---- Helpers ---------------------------------------------------------------------------------

    /**
     * Writes the panel's sample clause on a concept: the concept's own code in its own value
     * set, or a BMI of 40 or more.
     */
    private static void writeTheSampleClauseOn(ConceptFacade concept) {
        ClauseExpressionBuilder builder = new ClauseExpressionBuilder();
        Clause codedPath = builder.In(builder.Code(concept), builder.ValueSetRef(concept));
        Clause computedPath = builder.GreaterOrEqual(
                builder.Property("BMI determination", "value"), builder.Quantity(40, "kg/m2"));
        builder.setExpression(builder.Or(codedPath, computedPath));
        ClauseStore.writeClause(concept, (DiTreeEntity) builder.build().sourceGraph(),
                KernelTerm.USER, KometTerm.DEVELOPMENT_MODULE, KernelTerm.DEVELOPMENT_PATH);
    }

    /** Writes a concept with no description into the store, in one committed transaction. */
    private static ConceptRecord writeConceptWithNoDescription(UUID uuid) {
        Transaction transaction = Transaction.make("A concept with no description");
        StampEntity<?> stamp = transaction.getStamp(State.ACTIVE, System.currentTimeMillis(),
                KernelTerm.USER.nid(), KometTerm.DEVELOPMENT_MODULE.nid(), KernelTerm.DEVELOPMENT_PATH.nid());
        ConceptRecord concept = ConceptRecord.build(uuid, stamp.versions().get(0));
        EntityService.get().putEntity(concept);
        transaction.addComponent(concept);
        transaction.commit();
        return concept;
    }

    /**
     * A view under which no concept of the starter data has a description. It is the default
     * view in every respect but one: its language coordinate looks for descriptions in the
     * comment pattern, which holds none.
     */
    private static ViewCalculator aViewThatSelectsNoDescription() {
        LanguageCoordinateRecord noDescriptions = LanguageCoordinateRecord.make(
                KernelTerm.ENGLISH_LANGUAGE.nid(),
                LongIds.list.of(KernelTerm.COMMENT_PATTERN.nid()),
                LongIds.list.of(KernelTerm.REGULAR_NAME_DESCRIPTION_TYPE.nid(),
                        KernelTerm.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE.nid()),
                LongIds.list.empty(),
                LongIds.list.empty());
        ViewCoordinateRecord coordinate = ViewCoordinateRecord.make(
                Coordinates.Stamp.DevelopmentLatest(),
                noDescriptions,
                Coordinates.Logic.ElPlusPlus(),
                Coordinates.Navigation.inferred(),
                Coordinates.Edit.Default());
        return ViewCalculatorWithCache.getCalculator(coordinate);
    }

    /**
     * Fails when the text holds a nid: a number of the shape the store assigns, a nid in one of
     * the forms it has been written in, or one of the given nids in decimal.
     */
    private static void assertNoNid(String what, String text, long... nids) {
        for (Pattern form : new Pattern[]{STORE_NID, ANGLE_BRACKET_NID, LABELLED_NID}) {
            Matcher matcher = form.matcher(text);
            if (matcher.find()) {
                fail(what + " holds a nid: \"" + matcher.group() + "\" in:\n" + text);
            }
        }
        for (long nid : nids) {
            if (text.contains(Long.toString(nid))) {
                fail(what + " holds the nid " + nid + " in:\n" + text);
            }
        }
    }
}
