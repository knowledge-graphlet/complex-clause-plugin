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
package network.ike.komet.complexclause.model;

import dev.ikm.tinkar.entity.graph.VertexIdentityTable;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.IdentityRule;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.IdentityRule.Successors;
import dev.ikm.tinkar.terms.ConceptFacade;
import network.ike.komet.complexclause.terms.ComplexClauseTerms;
import org.eclipse.collections.api.factory.Lists;

/**
 * The identity table of clause trees (arch-vertex-identity-table; IKE-Network/ike-issues#1310),
 * provided to tinkar-core's write path as a {@link VertexIdentityTable} service, so a clause
 * written twice keeps the UUIDs of the vertices it continues.
 *
 * <p>By the identity rule (arch-vertex-identity-rule):
 * <ul>
 *   <li>{@code AND} and {@code OR} are commutative: their operands are unordered and told apart
 *       by key, so a retrieve is identified by its resource type and value set, a property by
 *       its path, a value-set reference and a code by their concept;
 *   <li>a comparison, {@code IN} and the interval operators are not: their operands are a pair,
 *       left and right, subject and value set, told apart by position;
 *   <li>{@code NOT}, {@code EXISTS}, {@code COUNT} and {@code SUM} take one operand, the root
 *       its one expression or none, a retrieve its {@code where} filter or none, and a property
 *       its source or none (the label form);
 *   <li>the literals are leaves, whose value and unit identify nothing and change by update.
 * </ul>
 *
 * <p>One exhaustive switch over {@link ClauseSemantic} with no {@code default} branch, so a
 * clause meaning added without its rule cannot build. Do not add one.
 */
public final class ClauseIdentityTable implements VertexIdentityTable {

    @Override
    public boolean covers(long meaningNid) {
        for (ClauseSemantic semantic : ClauseSemantic.values()) {
            if (semantic.nid == meaningNid) {
                return true;
            }
        }
        return false;
    }

    @Override
    public IdentityRule rule(long meaningNid) {
        return rule(ClauseSemantic.get(meaningNid));
    }

    /**
     * The table's entry for a clause meaning.
     *
     * @param semantic the meaning
     * @return its rule
     */
    public static IdentityRule rule(ClauseSemantic semantic) {
        return switch (semantic) {
            case AND, OR -> rule(Successors.OPERANDS);
            case EQUAL, GREATER, GREATER_OR_EQUAL, LESS, LESS_OR_EQUAL, IN,
                 INTERVAL_IN, INCLUDED_IN, OVERLAPS -> rule(Successors.ORDERED);
            case NOT, EXISTS, COUNT, SUM -> rule(Successors.SOLE);
            case ROOT -> rule(Successors.OPTIONAL);
            case RETRIEVE -> rule(Successors.OPTIONAL, ComplexClauseTerms.RESOURCE_TYPE, ComplexClauseTerms.OPERAND_CONCEPT);
            case PROPERTY -> rule(Successors.OPTIONAL, ComplexClauseTerms.RETRIEVE_PATH);
            case VALUE_SET_REF, CODE -> rule(Successors.NONE, ComplexClauseTerms.OPERAND_CONCEPT);
            case QUANTITY, INTEGER, BOOLEAN -> rule(Successors.NONE);
        };
    }

    private static IdentityRule rule(Successors successors, ConceptFacade... identifying) {
        return new IdentityRule(successors, Lists.immutable.of(identifying));
    }

    @Override
    public String toString() {
        return "ClauseSemantic";
    }
}
