/*
 *  Copyright: (c) 2019 Mayo Foundation for Medical Education and
 *  Research (MFMER). All rights reserved. MAYO, MAYO CLINIC, and the
 *  triple-shield Mayo logo are trademarks and service marks of MFMER.
 *
 *  Except as contained in the copyright notice above, or as used to identify
 *  MFMER as the author of this software, the trade names, trademarks, service
 *  marks, or product names of the copyright holder shall not be used in
 *  advertising, promotion or otherwise in connection with this software without
 *  prior written authorization of the copyright holder.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.ohnlp.elasticsearchnlp.scoring;

import org.ohnlp.elasticsearchnlp.ElasticsearchNLPPlugin;
import org.ohnlp.elasticsearchnlp.config.components.ConTextConfig;
import org.ohnlp.elasticsearchnlp.payloads.NLPPayload;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.util.BytesRef;

import java.util.Arrays;

public class NLPPayloadScoringWeightFunction {

    private static boolean initialized = false;

    private static double mismatch_neg;
    private static double mismatch_temp_light;
    private static double mismatch_temp_heavy;
    private static double mismatch_assert_light;
    private static double mismatch_assert_heavy;
    private static double mismatch_subj;

    private static double match_neg;
    private static double match_temp_light;
    private static double match_temp_heavy;
    private static double match_assert_light;
    private static double match_assert_heavy;
    private static double match_subj;

    /**
     * Generates a weight by which the original term similarity can be modified
     * @param queryPyldByteRef The {@link BytesRef} representing the original query payload
     * @param idxPyldByteRef The {@link BytesRef} representing the payload of the term in the index
     * @return A float weight denoting the individual term score
     */
    public static double getScoreMultiplier(BytesRef queryPyldByteRef, BytesRef idxPyldByteRef) {
        // Ensure plugin initialized before loading config. We are guaranteed this method is not run prior to full config
        // init
        if (!initialized) {
            initializeWeights();
        }
        // Load bytesref into java POJO
        NLPPayload queryPyld = new NLPPayload(queryPyldByteRef);
        NLPPayload idxPyld = new NLPPayload(idxPyldByteRef);
        double ret = 1.00d;
        if (ElasticsearchNLPPlugin.CONFIG.enableConTextSupport()) {
            // Check and modify scores based on ConText
            // Check Negation Status - Remove from consideration if mismatch
            if (queryPyld.isPositive() != idxPyld.isPositive()) {
                ret *= mismatch_neg;
            } else {
                ret *= match_neg;
            }
            // Check Subject - Remove from consideration if mismatch, Heavily weight if match
            if (queryPyld.patientIsSubject() != idxPyld.patientIsSubject()) {
                ret *= mismatch_subj;
            } else {
                ret *= match_subj;
            }
            // Check Historical -  Penalize if Mismatch Heavily if Query Looks for Historical, Lightly Otherwise
            if (queryPyld.isPresent() != idxPyld.isPresent()) {
                ret *= queryPyld.isPresent() ? mismatch_temp_light : mismatch_temp_heavy;
            } else {
                ret *= queryPyld.isPresent() ? match_temp_light : match_temp_heavy;
            }
            // Check Assertion - Penalize mismatch Heavily if Query Looks for not Asserted, Lightly Otherwise
            if (queryPyld.isAsserted() != idxPyld.isAsserted()) {
                ret *= queryPyld.isAsserted() ? mismatch_assert_light : mismatch_assert_heavy;
            } else {
                ret *= queryPyld.isAsserted() ? match_assert_light : match_assert_heavy;
            }
        }
        if (ElasticsearchNLPPlugin.CONFIG.enableEmbeddings()) {
            double scoreWeight = ElasticsearchNLPPlugin.CONFIG.getSettings().getEmbeddings().getScore_weight();
            float[] queryEmbs = queryPyld.getEmbeddings();
            float[] indexEmbs = queryPyld.getEmbeddings();
            double sim = cosSim(queryEmbs, indexEmbs);
            ret = (ret * (1 - scoreWeight)) + (sim * scoreWeight);
        }
        return ret;
    }

    /**
     * Calculates the cosine similarity between arg1 and arg2
     * @param arg1 vector 1
     * @param arg2 vector 2
     * @return The cosine similarity
     * @throws IllegalArgumentException if arg1.length != arg2.length
     */
    private static double cosSim(float[] arg1, float[] arg2) {
        if (arg1.length != arg2.length) {
            throw new IllegalArgumentException("Embeddings dimensions differ! Arg 1 embeddings length: " + arg1.length
                    + " Arg 2 embeddings length: " + arg2.length);
        }
        double dot = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;
        for (int i = 0; i < arg1.length; i++) {
            dot += arg1[i] * arg2[i];
            norm1 += Math.pow(arg1[i], 2);
            norm2 += Math.pow(arg2[i], 2);
        }
        return dot / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    private static void initializeWeights() {
        ConTextConfig.ConTextWeights mismatch =
                ElasticsearchNLPPlugin.CONFIG.getSettings().getContext().getWeights().mismatch;
        mismatch_neg = mismatch.getNegation();
        mismatch_assert_heavy = mismatch.getAssertion().getHeavy();
        mismatch_assert_light = mismatch.getAssertion().getLight();
        mismatch_temp_heavy = mismatch.getTemporal().getHeavy();
        mismatch_temp_light = mismatch.getTemporal().getLight();
        mismatch_subj = mismatch.getSubject();

        ConTextConfig.ConTextWeights match =
                ElasticsearchNLPPlugin.CONFIG.getSettings().getContext().getWeights().match;
        match_neg = match.getNegation();
        match_assert_light = match.getAssertion().getLight();
        match_assert_heavy = match.getAssertion().getHeavy();
        match_temp_light = match.getTemporal().getLight();
        match_temp_heavy = match.getTemporal().getHeavy();
        match_subj = match.getSubject();
    }

    /**
     * Generates an explanation for the given score multiplier
     * @param queryPyld The bytes corresponding to the query NLP payload
     * @param idxPyld The bytes corresponding to the NLP payload of the term being matched against in the index
     * @return An explanation for how the float weight is derived
     */
    public static Explanation generateExplanation(BytesRef queryPyld, BytesRef idxPyld) {
        double weight = getScoreMultiplier(queryPyld, idxPyld);
        // Load bytesref into java POJO
        byte[] queryPyldBytes = Arrays.copyOfRange(queryPyld.bytes, queryPyld.offset, queryPyld.offset + queryPyld.length);
        byte[] idxPyldBytes = Arrays.copyOfRange(idxPyld.bytes, idxPyld.offset, idxPyld.offset + idxPyld.length);
        NLPPayload queryPyldPojo = new NLPPayload(queryPyldBytes);
        NLPPayload idxPyldPojo = new NLPPayload(idxPyldBytes);
        if (weight > 0) { // TODO an arbritary delimiter for match/don't match... does this actually matter? Should not affect scoring
            return Explanation.match(weight, "weight(Query NLP Payload=" + queryPyldPojo + ", Idx NLP Payload=" + idxPyldPojo + ") returned a weight of " + weight);
        } else {
            return Explanation.noMatch("weight(Query NLP Payload=" + queryPyldPojo + ", Idx NLP Payload=" + idxPyldPojo + ") returned a weight of " + weight);
        }
    }
}
