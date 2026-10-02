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

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.coordinate.view.calculator.ViewCalculator;
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.EntityProxy;

import java.util.Optional;
import java.util.UUID;

/**
 * The text forms of a concept in what this plugin writes: the CQL explicated from a clause, the
 * labels of the panel, and the messages of its exceptions. None of them ever contains a nid
 * ({@code IKE-Network/ike-issues#1185}).
 *
 * <p>A nid is local to one store. CQL is the portable form of a clause — it is kept, and taken
 * to systems that hold another store or none — so a value set, a {@code define}, or a code that
 * is named by a nid names nothing there. Text therefore identifies a concept by a description,
 * and when it has none, by its public id. These are the rules the assistant and tinkar-service
 * follow for their text ({@code IKE-Network/ike-issues#1170}, {@code IKE-Network/ike-issues#1177}).
 */
public final class ConceptText {

    /** The text written for a component the store has no public id for; it has no identifier in it. */
    public static final String UNIDENTIFIED = "unidentified component";

    private ConceptText() {
    }

    /**
     * The name of a concept: the description the view selects; when the view selects none, or
     * there is no view, the description the concept's proxy was made with or the store's own;
     * and when there is no description at all, the concept's {@link #identifier(ConceptFacade)
     * identifier}.
     *
     * @param concept the concept
     * @param view    the view that selects the description; may be null
     * @return a description, else the first UUID, else {@link #UNIDENTIFIED}; never a nid
     */
    public static String name(ConceptFacade concept, ViewCalculator view) {
        return selectedBy(view, concept)
                .or(() -> ownDescription(concept))
                .orElseGet(() -> identifier(concept));
    }

    /**
     * The identifier written for a concept: its first UUID.
     *
     * @param concept the concept
     * @return the first UUID as a string, or {@link #UNIDENTIFIED} when the concept has no
     *         public id; never a nid
     */
    public static String identifier(ConceptFacade concept) {
        try {
            return firstUuid(concept.publicId());
        } catch (RuntimeException unresolvable) {
            return UNIDENTIFIED;
        }
    }

    /**
     * The identifier written for a component known only by its nid: its first UUID.
     *
     * @param nid the component's nid in the open store
     * @return the first UUID as a string, or {@link #UNIDENTIFIED} when the store has no public
     *         id for the nid; never a nid
     */
    public static String identifier(int nid) {
        try {
            return firstUuid(PrimitiveData.publicId(nid));
        } catch (RuntimeException unresolvable) {
            return UNIDENTIFIED;
        }
    }

    /** The description the view selects for the concept, when there is a view and it selects one. */
    private static Optional<String> selectedBy(ViewCalculator view, ConceptFacade concept) {
        if (view == null) {
            return Optional.empty();
        }
        try {
            return described(view.getDescriptionText(concept.nid()));
        } catch (RuntimeException noDescription) {
            return Optional.empty();
        }
    }

    /**
     * The description a proxy was made with, or the store's own description of the concept.
     * Neither is the store's default text for a concept with no description, which is its nid
     * in angle brackets.
     */
    private static Optional<String> ownDescription(ConceptFacade concept) {
        try {
            if (concept instanceof EntityProxy proxy) {
                return described(Optional.ofNullable(proxy.description()));
            }
            return described(PrimitiveData.textOptional(concept.nid()));
        } catch (RuntimeException noDescription) {
            return Optional.empty();
        }
    }

    /** The first UUID of a public id, or {@link #UNIDENTIFIED} when it holds none. */
    private static String firstUuid(PublicId publicId) {
        if (publicId == null) {
            return UNIDENTIFIED;
        }
        UUID[] uuids = publicId.asUuidArray();
        return uuids.length > 0 ? uuids[0].toString() : UNIDENTIFIED;
    }

    /** A description that is present and not blank. */
    private static Optional<String> described(Optional<String> text) {
        return text.filter(value -> !value.isBlank());
    }
}
