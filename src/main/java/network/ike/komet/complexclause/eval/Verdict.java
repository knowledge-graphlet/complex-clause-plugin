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
package network.ike.komet.complexclause.eval;

/**
 * The three-valued outcome of evaluating a clause against a data set, mapping onto CQL/ELM's
 * null-as-unknown semantics: {@link #MET} = {@code true}, {@link #NOT_MET} = {@code false},
 * {@link #INDETERMINATE} = {@code null} (data absent or insufficient to decide).
 */
public enum Verdict {
    MET,
    NOT_MET,
    INDETERMINATE
}
