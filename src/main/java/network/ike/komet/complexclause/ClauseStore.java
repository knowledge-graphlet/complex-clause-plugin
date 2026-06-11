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
import dev.ikm.tinkar.composer.Composer;
import dev.ikm.tinkar.composer.Session;
import dev.ikm.tinkar.composer.assembler.SemanticAssembler;
import dev.ikm.tinkar.coordinate.stamp.calculator.Latest;
import dev.ikm.tinkar.coordinate.stamp.calculator.StampCalculator;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.SemanticEntityVersion;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.EntityProxy;
import dev.ikm.tinkar.terms.State;
import network.ike.komet.complexclause.bootstrap.ComplexClauseBootstrap;
import network.ike.komet.complexclause.model.ClauseExpression;
import network.ike.komet.complexclause.terms.ComplexClauseTerms;

import java.util.Optional;
import java.util.UUID;

/**
 * Reads and writes the single complex-concept-clause semantic on a concept (the Layer-2 facet),
 * the clause analogue of the EL++ stated/inferred axiom semantics. There is one clause semantic per
 * concept, keyed by a stable single-semantic id so re-authoring updates in place.
 */
public final class ClauseStore {

    private ClauseStore() {
    }

    /**
     * Reads the latest complex-concept clause on a concept.
     *
     * @param concept the concept
     * @param stamp   the stamp calculator (a {@code ViewCalculator} works) selecting the version
     * @return the clause expression, or empty if the concept has no clause semantic
     */
    public static Optional<ClauseExpression> readClause(ConceptFacade concept, StampCalculator stamp) {
        int[] semanticNids = EntityService.get().semanticNidsForComponentOfPattern(
                concept.nid(), ComplexClauseTerms.COMPLEX_CONCEPT_CLAUSE_PATTERN.nid());
        for (int semanticNid : semanticNids) {
            Latest<SemanticEntityVersion> latest = stamp.latest(semanticNid);
            if (latest.isPresent() && !latest.get().fieldValues().isEmpty()
                    && latest.get().fieldValues().get(0) instanceof DiTreeEntity diTree) {
                return Optional.of(ClauseExpression.from(diTree));
            }
        }
        return Optional.empty();
    }

    /** The stable single-semantic id for a concept's clause semantic. */
    public static UUID clauseSemanticUuid(ConceptFacade concept) {
        return UuidT5Generator.singleSemanticUuid(
                ComplexClauseTerms.COMPLEX_CONCEPT_CLAUSE_PATTERN.publicId(), concept.publicId());
    }

    /**
     * Writes (or updates) the complex-concept clause semantic on a concept.
     *
     * @param concept     the concept the clause constrains
     * @param clauseGraph the built clause graph (from {@code ClauseExpressionBuilder.asDiTree()})
     * @param author      the STAMP author
     * @param module      the STAMP module
     * @param path        the STAMP path
     */
    public static void writeClause(ConceptFacade concept, DiTreeEntity clauseGraph,
                                   EntityProxy.Concept author, EntityProxy.Concept module, EntityProxy.Concept path) {
        ComplexClauseBootstrap.ensureBootstrapped();
        EntityProxy reference = concept instanceof EntityProxy entityProxy
                ? entityProxy
                : EntityProxy.Concept.make(PrimitiveData.text(concept.nid()), concept.publicId());
        EntityProxy.Semantic semantic = EntityProxy.Semantic.make(PublicIds.of(clauseSemanticUuid(concept)));
        Composer composer = new Composer("complex-clause-write");
        Session session = composer.open(State.ACTIVE, author, module, path);
        session.compose((SemanticAssembler assembler) -> assembler
                .semantic(semantic)
                .reference(reference)
                .pattern(ComplexClauseTerms.COMPLEX_CONCEPT_CLAUSE_PATTERN)
                .fieldValues(values -> values.add(clauseGraph)));
        composer.commitSession(session);
    }
}
