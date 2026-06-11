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
package network.ike.komet.complexclause.model;

import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.terms.ConceptFacade;
import network.ike.komet.complexclause.terms.ComplexClauseTerms;

/**
 * Connects a clause-vertex meaning concept to its operator kind, mirroring
 * {@code dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalAxiomSemantic}. Each constant's
 * {@link #nid} is resolved from its meaning concept at class-load time, so a Tinkar datastore must
 * be running before this enum is referenced (the same contract the EL++ {@code LogicalAxiomSemantic}
 * carries). The operator set mirrors the HL7 ELM starter set named in {@code anf-gigaassay-variant-knowledge}.
 */
public enum ClauseSemantic {
    ROOT(ComplexClauseTerms.CLAUSE_DEFINITION_ROOT),
    AND(ComplexClauseTerms.CLAUSE_AND),
    OR(ComplexClauseTerms.CLAUSE_OR),
    NOT(ComplexClauseTerms.CLAUSE_NOT),
    EQUAL(ComplexClauseTerms.CLAUSE_EQUAL),
    GREATER(ComplexClauseTerms.CLAUSE_GREATER),
    GREATER_OR_EQUAL(ComplexClauseTerms.CLAUSE_GREATER_OR_EQUAL),
    LESS(ComplexClauseTerms.CLAUSE_LESS),
    LESS_OR_EQUAL(ComplexClauseTerms.CLAUSE_LESS_OR_EQUAL),
    EXISTS(ComplexClauseTerms.CLAUSE_EXISTS),
    IN(ComplexClauseTerms.CLAUSE_IN),
    VALUE_SET_REF(ComplexClauseTerms.CLAUSE_VALUE_SET_REF),
    RETRIEVE(ComplexClauseTerms.CLAUSE_RETRIEVE),
    PROPERTY(ComplexClauseTerms.CLAUSE_PROPERTY),
    COUNT(ComplexClauseTerms.CLAUSE_COUNT),
    SUM(ComplexClauseTerms.CLAUSE_SUM),
    INTERVAL_IN(ComplexClauseTerms.CLAUSE_INTERVAL_IN),
    INCLUDED_IN(ComplexClauseTerms.CLAUSE_INCLUDED_IN),
    OVERLAPS(ComplexClauseTerms.CLAUSE_OVERLAPS),
    QUANTITY(ComplexClauseTerms.CLAUSE_QUANTITY),
    INTEGER(ComplexClauseTerms.CLAUSE_INTEGER),
    BOOLEAN(ComplexClauseTerms.CLAUSE_BOOLEAN),
    CODE(ComplexClauseTerms.CLAUSE_CODE);

    /** The meaning concept stored as the vertex's {@code meaningNid}. */
    public final ConceptFacade meaning;
    /** The meaning concept's nid (resolved at class-load). */
    public final int nid;

    ClauseSemantic(ConceptFacade meaning) {
        this.meaning = meaning;
        this.nid = meaning.nid();
    }

    /**
     * Resolves the operator kind for a vertex meaning nid.
     *
     * @param meaningNid the vertex's {@code meaningNid}
     * @return the matching operator kind
     * @throws IllegalStateException if the nid is not a known clause operator
     */
    public static ClauseSemantic get(int meaningNid) {
        for (ClauseSemantic semantic : values()) {
            if (semantic.nid == meaningNid) {
                return semantic;
            }
        }
        throw new IllegalStateException("No clause semantic for nid: " + meaningNid + " " + PrimitiveData.text(meaningNid));
    }

    /**
     * Resolves the operator kind for a meaning concept.
     *
     * @param meaning the meaning concept
     * @return the matching operator kind
     */
    public static ClauseSemantic get(ConceptFacade meaning) {
        return get(meaning.nid());
    }

    /** @return {@code true} for the five binary comparison operators. */
    public boolean isComparison() {
        return this == EQUAL || this == GREATER || this == GREATER_OR_EQUAL || this == LESS || this == LESS_OR_EQUAL;
    }

    /** @return {@code true} for the four literal kinds. */
    public boolean isLiteral() {
        return this == QUANTITY || this == INTEGER || this == BOOLEAN || this == CODE;
    }

    /** @return {@code true} for the interval/temporal operators. */
    public boolean isIntervalOperator() {
        return this == INTERVAL_IN || this == INCLUDED_IN || this == OVERLAPS;
    }
}
