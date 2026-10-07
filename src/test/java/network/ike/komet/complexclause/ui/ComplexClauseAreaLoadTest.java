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
package network.ike.komet.complexclause.ui;

import dev.ikm.tinkar.terms.KernelTerm;
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.terms.ConceptFacade;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The panel's Load finds the concept a typed UUID names, and loads nothing for a UUID the
 * knowledge base does not hold ({@code IKE-Network/ike-issues#1185}).
 *
 * <p>Load used to ask the store for a nid for whatever UUID was typed. That assigns a nid to a
 * UUID the knowledge base does not hold; the concept then loaded, and a clause could be saved
 * on it.
 */
class ComplexClauseAreaLoadTest {

    @BeforeAll
    static void startDatastore() {
        PrimitiveData.selectControllerByName("Load Ephemeral Store");
        PrimitiveData.start();
    }

    @AfterAll
    static void stopDatastore() {
        PrimitiveData.stop();
    }

    @Test
    void aUuidTheKnowledgeBaseHoldsIsLoaded() {
        long nid = KernelTerm.ENGLISH_LANGUAGE.nid();
        // Any of the concept's UUIDs loads it (English Language has three).
        for (UUID uuid : KernelTerm.ENGLISH_LANGUAGE.publicId().asUuidArray()) {
            Optional<ConceptFacade> concept = ComplexClauseArea.conceptFor(uuid.toString());

            assertTrue(concept.isPresent(), "loaded through " + uuid);
            assertTrue(PublicId.equals(KernelTerm.ENGLISH_LANGUAGE.publicId(), concept.get().publicId()));
            assertEquals(nid, concept.get().nid());
        }
    }

    @Test
    void aUuidTheKnowledgeBaseDoesNotHoldIsNotLoadedAndIsAssignedNoNid() {
        UUID unknown = UUID.randomUUID();

        assertTrue(ComplexClauseArea.conceptFor(unknown.toString()).isEmpty());

        assertFalse(PrimitiveData.get().hasUuid(unknown),
                "looking a typed UUID up must not assign a nid to one the knowledge base does not hold");
    }

    @Test
    void textThatIsNotAUuidIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> ComplexClauseArea.conceptFor("not a uuid"));
    }
}
