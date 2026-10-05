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

import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.util.uuid.UuidT5Generator;
import dev.ikm.tinkar.entity.ConceptRecord;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.StampEntity;
import dev.ikm.tinkar.entity.transaction.Transaction;
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.EntityProxy;
import dev.ikm.tinkar.terms.State;
import dev.ikm.tinkar.terms.TinkarTerm;
import network.ike.komet.complexclause.cql.ClauseToCqlProjector;
import network.ike.komet.complexclause.model.ClauseExpressionBuilder;
import network.ike.komet.complexclause.model.ClauseSemantic;
import network.ike.komet.complexclause.model.ConceptText;
import network.ike.komet.complexclause.terms.ComplexClauseTerms;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The text this plugin writes holds no nid ({@code IKE-Network/ike-issues#1185}): the CQL
 * explicated from a clause, the name of a concept, and the messages of its exceptions.
 *
 * <p>The tests run against an empty in-memory store, with no view. A concept is then named by
 * the description its proxy was made with, and a concept with none is the case under test: it
 * used to be named by the store's default text, which is its nid in angle brackets.
 *
 * <p>The in-memory store numbers components upward from {@code Integer.MIN_VALUE + 1}, so in
 * decimal every nid it assigns is a minus sign and ten digits beginning {@code 21474} or
 * {@code 21473}. {@link #assertNoNid} looks for a number of that shape and for the forms a nid
 * has been written in: {@code <nid>} and {@code nid:N}.
 */
class NidFreeTextTest {

    /** A nid of the in-memory store in decimal: {@code Integer.MIN_VALUE} plus a small count. */
    private static final Pattern STORE_NID = Pattern.compile("-2147[34]\\d{5}(?!\\d)");

    /** The form {@code PrimitiveData.text} writes for a component with no description. */
    private static final Pattern ANGLE_BRACKET_NID = Pattern.compile("<-?\\d+>");

    /** A number labelled as a nid: {@code nid:N}, {@code nid N}, {@code nid=N}. */
    private static final Pattern LABELLED_NID = Pattern.compile("(?i)\\bnid\\b\\s*[=:]?\\s*-?\\d+");

    /**
     * A nid the store has assigned to no component, so it has no public id for it: the store
     * numbers components upward from the bottom of the int range and never reaches the top.
     */
    private static final int UNASSIGNED_NID = Integer.MAX_VALUE - 1;

    @BeforeAll
    static void startDatastore() {
        PrimitiveData.selectControllerByName("Load Ephemeral Store");
        PrimitiveData.start();
    }

    @AfterAll
    static void stopDatastore() {
        PrimitiveData.stop();
    }

    // ---- The name of a concept -------------------------------------------------------------------

    @Test
    void aConceptIsNamedByTheDescriptionItsProxyWasMadeWith() {
        ConceptFacade morbidObesity = described("Morbid obesity");

        assertEquals("Morbid obesity", ConceptText.name(morbidObesity, null));
    }

    @Test
    void aConceptWithNoDescriptionIsNamedByItsUuid() {
        UUID uuid = uuid("undescribed proxy");
        ConceptFacade undescribed = EntityProxy.Concept.make(PublicIds.of(uuid));

        String name = ConceptText.name(undescribed, null);

        assertEquals(uuid.toString(), name);
        assertEquals(uuid.toString(), ConceptText.identifier(undescribed));
        assertNoNid("the name", name, undescribed.nid());
    }

    @Test
    void aConceptInTheStoreWithNoDescriptionIsNamedByItsUuid() {
        UUID uuid = uuid("undescribed concept in the store");
        ConceptRecord concept = writeConceptWithNoDescription(uuid);

        // What the name used to be made from. The assertion fails when tinkar-core's own default
        // text stops being the nid, which is the moment to reconsider ConceptText.
        assertEquals("<" + concept.nid() + ">", concept.description());

        String name = ConceptText.name(concept, null);
        assertEquals(uuid.toString(), name);
        assertEquals(uuid.toString(), ConceptText.identifier(concept.nid()));
        assertNoNid("the name", name, concept.nid());
    }

    @Test
    void aComponentTheStoreHasNoPublicIdForIsStatedAsUnidentified() {
        assertEquals("unidentified component", ConceptText.identifier(UNASSIGNED_NID));
        assertEquals(ConceptText.UNIDENTIFIED, ConceptText.identifier(EntityProxy.Concept.make(UNASSIGNED_NID)));
        assertEquals(ConceptText.UNIDENTIFIED, ConceptText.name(EntityProxy.Concept.make(UNASSIGNED_NID), null));
    }

    // ---- CQL -------------------------------------------------------------------------------------

    @Test
    void cqlNamesAConceptWithNoDescriptionByItsUuid() {
        UUID uuid = uuid("undescribed value set class");
        ConceptFacade condition = described("Condition");
        ConceptFacade undescribed = EntityProxy.Concept.make(PublicIds.of(uuid));

        ClauseExpressionBuilder builder = new ClauseExpressionBuilder();
        builder.setExpression(builder.Or(
                builder.Exists(builder.Retrieve(condition, undescribed)),
                builder.In(builder.Code(undescribed), builder.ValueSetRef(undescribed))));

        // As the panel explicates a clause: the define is named for the concept, and the value
        // set's ECL for its class.
        String cql = new ClauseToCqlProjector(layer1 -> "<< " + ConceptText.name(layer1, null))
                .project(builder.build(), ConceptText.name(undescribed, null));

        assertEquals("valueset \"" + uuid + "\" = ECL{ << " + uuid + " }\n"
                        + "\n"
                        + "define \"" + uuid + "\":\n"
                        + "  (exists [Condition: \"" + uuid + "\"] or (\"" + uuid + "\" in \"" + uuid + "\"))\n",
                cql);
        assertNoNid("the CQL", cql, undescribed.nid(), condition.nid());
    }

    @Test
    void cqlOfDescribedConceptsReadsAsItDid() {
        ConceptFacade condition = described("Condition");
        ConceptFacade morbidObesity = described("Morbid obesity");

        ClauseExpressionBuilder builder = new ClauseExpressionBuilder();
        builder.setExpression(builder.Exists(builder.Retrieve(condition, morbidObesity)));

        String cql = new ClauseToCqlProjector(layer1 -> "<< " + ConceptText.name(layer1, null))
                .project(builder.build(), "Has morbid obesity");

        assertEquals("valueset \"Morbid obesity\" = ECL{ << Morbid obesity }\n"
                        + "\n"
                        + "define \"Has morbid obesity\":\n"
                        + "  exists [Condition: \"Morbid obesity\"]\n",
                cql);
        assertNoNid("the CQL", cql, condition.nid(), morbidObesity.nid());
    }

    // ---- Exception messages ----------------------------------------------------------------------

    @Test
    void aConceptWithNoClauseIsNamedByItsUuidInTheMessage() {
        UUID uuid = uuid("concept with no clause");
        ConceptFacade concept = EntityProxy.Concept.make(PublicIds.of(uuid));

        IllegalStateException noClause = assertThrows(IllegalStateException.class,
                () -> ClauseToCqlProjector.project(concept, null));

        assertEquals("No complex-concept clause semantic on concept " + uuid, noClause.getMessage());
        assertNoNid("the message", noClause.getMessage(), concept.nid());
    }

    @Test
    void aVertexMeaningThatIsNoClauseOperatorIsNamedByItsUuidInTheMessage() {
        int notAnOperator = TinkarTerm.ENGLISH_LANGUAGE.nid();

        IllegalStateException noOperator = assertThrows(IllegalStateException.class,
                () -> ClauseSemantic.get(notAnOperator));

        assertEquals("No clause semantic for " + TinkarTerm.ENGLISH_LANGUAGE.publicId().leastUuid(),
                noOperator.getMessage());
        assertNoNid("the message", noOperator.getMessage(), notAnOperator);

        IllegalStateException unidentified = assertThrows(IllegalStateException.class,
                () -> ClauseSemantic.get(UNASSIGNED_NID));
        assertEquals("No clause semantic for unidentified component", unidentified.getMessage());
        assertFalse(unidentified.getMessage().contains(Integer.toString(UNASSIGNED_NID)));
    }

    // ---- Helpers ---------------------------------------------------------------------------------

    /** A concept the store does not hold, with a description of its own. */
    private static ConceptFacade described(String name) {
        return EntityProxy.Concept.make(name, uuid(name));
    }

    /** A UUID for a component the tests name: the same in every run. */
    private static UUID uuid(String what) {
        return UuidT5Generator.get(ComplexClauseTerms.NAMESPACE, "nid-free-text-test:" + what);
    }

    /** Writes a concept with no description into the store, in one committed transaction. */
    private static ConceptRecord writeConceptWithNoDescription(UUID uuid) {
        Transaction transaction = Transaction.make("A concept with no description");
        StampEntity<?> stamp = transaction.getStamp(State.ACTIVE, System.currentTimeMillis(),
                TinkarTerm.USER.nid(), TinkarTerm.DEVELOPMENT_MODULE.nid(), TinkarTerm.DEVELOPMENT_PATH.nid());
        ConceptRecord concept = ConceptRecord.build(uuid, stamp.versions().get(0));
        EntityService.get().putEntity(concept);
        transaction.addComponent(concept);
        transaction.commit();
        return concept;
    }

    /**
     * Fails when the text holds a nid: a number of the shape the store assigns, a nid in one of
     * the forms it has been written in, or one of the given nids in decimal.
     */
    private static void assertNoNid(String what, String text, int... nids) {
        for (Pattern form : new Pattern[]{STORE_NID, ANGLE_BRACKET_NID, LABELLED_NID}) {
            Matcher matcher = form.matcher(text);
            if (matcher.find()) {
                fail(what + " holds a nid: \"" + matcher.group() + "\" in:\n" + text);
            }
        }
        for (int nid : nids) {
            if (text.contains(Integer.toString(nid))) {
                fail(what + " holds the nid " + nid + " in:\n" + text);
            }
        }
    }
}
