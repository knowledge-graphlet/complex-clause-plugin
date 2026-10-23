/*
 * Copyright © 2015 Integrated Knowledge Management (support@ikm.dev)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package network.ike.komet.complexclause;

import java.io.File;
import dev.ikm.tinkar.entity.load.LoadEntitiesFromProtobufFile;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.util.uuid.UuidT5Generator;
import dev.ikm.tinkar.entity.EntityHandle;
import dev.ikm.tinkar.entity.SemanticEntity;
import dev.ikm.tinkar.entity.SemanticEntityVersion;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.entity.graph.EntityVertex;
import dev.ikm.tinkar.entity.graph.VertexDerivation;
import dev.ikm.tinkar.entity.graph.VertexIdentityTable;
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.EntityProxy;
import dev.ikm.tinkar.terms.KernelConcept;
import network.ike.komet.complexclause.model.Clause;
import network.ike.komet.complexclause.model.ClauseExpressionBuilder;
import network.ike.komet.complexclause.model.ClauseIdentityTable;
import network.ike.komet.complexclause.terms.ComplexClauseTerms;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Clause trees keep their vertex identities when re-authored (arch-vertex-identity-rule;
 * IKE-Network/ike-issues#1310): tinkar-core's write path finds {@link ClauseIdentityTable} as a
 * provided service and correlates each new version of a clause with the ones before it.
 */
class ClauseIdentityTest {

    @BeforeAll
    static void startDatastore() {
        PrimitiveData.selectControllerByName("Load Memory-Only 64-bit Store");
        PrimitiveData.start();
        // The starter set: the kernel concepts and patterns the clauses and their STAMPs refer to.
        new LoadEntitiesFromProtobufFile(new File("target/data/ike-starter-set-reasoned-pb.zip")).compute();
    }

    @AfterAll
    static void stopDatastore() {
        PrimitiveData.stop();
    }

    private static ConceptFacade concept(String name) {
        return EntityProxy.Concept.make(name, UuidT5Generator.get(ComplexClauseTerms.NAMESPACE, "identity:" + name));
    }

    private static DiTreeEntity clause(Function<ClauseExpressionBuilder, Clause> expression) {
        ClauseExpressionBuilder builder = new ClauseExpressionBuilder();
        builder.setExpression(expression.apply(builder));
        return builder.asDiTree();
    }

    private static void write(ConceptFacade concept, DiTreeEntity tree) {
        ClauseStore.writeClause(concept, tree, KernelConcept.USER, KernelConcept.PRIMORDIAL_MODULE,
                KernelConcept.DEVELOPMENT_PATH);
    }

    /** The stored clause trees of a concept, oldest first. */
    private static List<DiTreeEntity> stored(ConceptFacade concept) {
        SemanticEntity<SemanticEntityVersion> semantic = EntityHandle.get(
                PublicIds.of(ClauseStore.clauseSemanticUuid(concept))).expectSemantic();
        List<SemanticEntityVersion> versions = new ArrayList<>(semantic.versions().castToList());
        versions.sort(Comparator.comparingLong(version -> version.stamp().time()));
        return versions.stream().map(version -> (DiTreeEntity) version.fieldValues().getFirst()).toList();
    }

    private static Set<UUID> uuids(DiTreeEntity tree) {
        Set<UUID> uuids = new HashSet<>();
        for (EntityVertex vertex : tree.vertexMap()) {
            uuids.add(vertex.asUuidList().getFirst());
        }
        return uuids;
    }

    @Test
    void theWritePathFindsTheClauseTable() {
        DiTreeEntity tree = clause(b -> b.GreaterOrEqual(b.Property("BMI determination", "value"), b.Quantity(40, "kg/m2")));
        assertInstanceOf(ClauseIdentityTable.class, VertexIdentityTable.forTree(tree).orElseThrow());
    }

    /** A literal changed in place is an update: every vertex keeps its UUID, the root derived. */
    @Test
    void aChangedLiteralKeepsEveryIdentity() {
        ConceptFacade constrained = concept("Changed literal");
        write(constrained, clause(b -> b.GreaterOrEqual(b.Property("BMI determination", "value"), b.Quantity(40, "kg/m2"))));
        write(constrained, clause(b -> b.GreaterOrEqual(b.Property("BMI determination", "value"), b.Quantity(35, "kg/m2"))));
        List<DiTreeEntity> versions = stored(constrained);
        assertEquals(2, versions.size());
        assertEquals(uuids(versions.get(0)), uuids(versions.get(1)));
        UUID root = versions.getFirst().root().asUuidList().getFirst();
        assertEquals(VertexDerivation.root(PublicIds.of(ClauseStore.clauseSemanticUuid(constrained)), 0), root,
                "the root derives from the clause semantic");
    }

    /** A comparison's sides are told apart by position, so swapping them updates both in place. */
    @Test
    void swappedSidesAreUpdatedInPlace() {
        ConceptFacade constrained = concept("Swapped sides");
        write(constrained, clause(b -> b.Less(b.IntegerLiteral(1), b.IntegerLiteral(2))));
        write(constrained, clause(b -> b.Less(b.IntegerLiteral(2), b.IntegerLiteral(1))));
        List<DiTreeEntity> versions = stored(constrained);
        assertEquals(uuids(versions.get(0)), uuids(versions.get(1)));
    }

    /** Identical sides, x = x, derive apart by position, and nothing is twice in a tree. */
    @Test
    void identicalSidesDeriveApart() {
        ConceptFacade constrained = concept("Identical sides");
        DiTreeEntity tree = clause(b -> b.Equal(b.IntegerLiteral(5), b.IntegerLiteral(5)));
        write(constrained, tree);
        DiTreeEntity stored = stored(constrained).getFirst();
        assertEquals(stored.vertexMap().size(), uuids(stored).size(), "no UUID twice");
        assertNotEquals(uuids(tree), uuids(stored), "the write path derived them");
    }

    /**
     * Beneath AND, operands are told apart by key, so a value set reference retargeted there is a
     * replace: it is born, the rest keep their UUIDs. As an IN's right side it is identified by
     * position instead, so the same change there is an update.
     */
    @Test
    void aRetargetedOperandIsReplacedAndARetargetedSideUpdated() {
        ConceptFacade fever = concept("Fever");
        ConceptFacade rash = concept("Rash");
        ConceptFacade cough = concept("Cough");

        ConceptFacade conjunction = concept("Retargeted conjunct");
        write(conjunction, clause(b -> b.And(b.ValueSetRef(fever), b.ValueSetRef(rash))));
        write(conjunction, clause(b -> b.And(b.ValueSetRef(fever), b.ValueSetRef(cough))));
        List<DiTreeEntity> operands = stored(conjunction);
        Set<UUID> kept = uuids(operands.get(1));
        kept.retainAll(uuids(operands.get(0)));
        assertEquals(operands.get(1).vertexMap().size() - 1, kept.size(), "root, AND and fever kept; cough born");

        ConceptFacade membership = concept("Retargeted value set");
        write(membership, clause(b -> b.In(b.Property("Condition", "code"), b.ValueSetRef(rash))));
        write(membership, clause(b -> b.In(b.Property("Condition", "code"), b.ValueSetRef(cough))));
        List<DiTreeEntity> sides = stored(membership);
        assertEquals(uuids(sides.get(0)), uuids(sides.get(1)), "the right side is updated in place");
    }

    /** The table stays an exhaustive switch: a clause meaning without a rule cannot build. */
    @Test
    void theTableHasNoDefaultBranch() throws IOException {
        Path source = Path.of("src/main/java/network/ike/komet/complexclause/model/ClauseIdentityTable.java");
        assertTrue(Files.exists(source), "the table's source, from the module directory: " + source.toAbsolutePath());
        Pattern defaultLabel = Pattern.compile("\\bdefault\\s*(->|:)");
        for (String line : Files.readAllLines(source)) {
            String trimmed = line.strip();
            if (trimmed.startsWith("*") || trimmed.startsWith("/*") || trimmed.startsWith("//")) {
                continue;
            }
            assertFalse(defaultLabel.matcher(line).find(), "no default branch: " + trimmed);
        }
    }
}
