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
package network.ike.komet.complexclause.bootstrap;

import dev.ikm.komet.terms.KometConcept;
import dev.ikm.tinkar.common.service.ServiceLifecycle;
import dev.ikm.tinkar.entity.EntityHandle;
import dev.ikm.tinkar.entity.transaction.StampedWriter;
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.KernelConcept;
import dev.ikm.tinkar.terms.State;
import network.ike.komet.complexclause.terms.ComplexClauseTerms;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Idempotently composes the complex-clause pattern and its HL7-ELM-mirroring operator vocabulary
 * into the open knowledge base, so clauses can be authored. Runs at application startup via the
 * {@link ServiceLifecycle} SPI (after the datastore is up) and can also be invoked lazily via
 * {@link #ensureBootstrapped()} the first time the tool area is used.
 *
 * <p>Bootstrapping is a no-op once the pattern exists. All concepts use stable type-5 ids
 * ({@link ComplexClauseTerms}), so the vocabulary is identical across machines and re-running is safe.
 */
public class ComplexClauseBootstrap implements ServiceLifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(ComplexClauseBootstrap.class);

    /** Public no-arg constructor required by {@link java.util.ServiceLoader}. */
    public ComplexClauseBootstrap() {
    }

    @Override
    public void startup() {
        // ServiceLifecycle contract: a thrown exception aborts application startup. The clause
        // vocabulary is not load-bearing for the rest of Komet, so failure must not be fatal.
        try {
            ensureBootstrapped();
        } catch (RuntimeException e) {
            LOG.error("Complex-clause vocabulary bootstrap failed", e);
        }
    }

    @Override
    public void shutdown() {
        // Nothing to release.
    }

    /** @return {@code true} once the complex-clause pattern is present in the datastore. */
    public static boolean isBootstrapped() {
        return EntityHandle.get(ComplexClauseTerms.COMPLEX_CONCEPT_CLAUSE_PATTERN)
                .entity().filter(e -> !e.canceled()).isPresent();
    }

    /**
     * Composes the pattern + operator vocabulary if not already present.
     *
     * @return {@code true} if a bootstrap was performed, {@code false} if it was already present
     */
    public static synchronized boolean ensureBootstrapped() {
        if (isBootstrapped()) {
            return false;
        }
        LOG.info("Bootstrapping complex-clause pattern + ELM operator vocabulary");
        try (StampedWriter writer = StampedWriter.open("complex-clause-bootstrap", State.ACTIVE,
                KernelConcept.USER, KometConcept.DEVELOPMENT_MODULE, KernelConcept.DEVELOPMENT_PATH)) {
            for (ConceptFacade concept : ComplexClauseTerms.allConcepts()) {
                writer.concept(concept.publicId());
                writer.fullyQualifiedName(concept.publicId(), concept.description());
            }
            writer.pattern(ComplexClauseTerms.COMPLEX_CONCEPT_CLAUSE_PATTERN.publicId(),
                    ComplexClauseTerms.COMPLEX_CONCEPT_CLAUSE, ComplexClauseTerms.COMPUTABLE_LOGIC,
                    StampedWriter.field(ComplexClauseTerms.CLAUSE_DEFINITION_GRAPH,
                            ComplexClauseTerms.COMPUTABLE_LOGIC, KernelConcept.DITREE_FIELD));
            writer.fullyQualifiedName(ComplexClauseTerms.COMPLEX_CONCEPT_CLAUSE_PATTERN.publicId(),
                    "Complex Concept Clause Pattern");
            writer.commit();
        }
        LOG.info("Complex-clause vocabulary bootstrap committed: {} concepts + 1 pattern",
                ComplexClauseTerms.allConcepts().length);
        return true;
    }
}
