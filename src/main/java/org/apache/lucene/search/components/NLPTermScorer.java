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

package org.apache.lucene.search.components;

import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.NumericDocValues;
import org.ohnlp.elasticsearchnlp.lucene.similarity.NLPDocScorer;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Weight;

import java.io.IOException;

public class NLPTermScorer extends Scorer {
    private final PostingsEnum postingsEnum;
    private final Scorer baseScorer;
    public final NLPDocScorer docScorer;
    private final NumericDocValues norms;

    /**
     * Construct a <code>TermScorer</code>.
     *
     * @param weight     The weight of the <code>Term</code> in the query.
     * @param td         An iterator over the documents matching the <code>Term</code>.
     * @param docScorer  The <code>Similarity.SimScorer</code> implementation
     *                   to be used for score computations.
     * @param baseScorer The base scorer that this scoring implementation wraps
     * @param reader
     */
    public NLPTermScorer(Weight weight, PostingsEnum td, NLPDocScorer docScorer, Scorer baseScorer, LeafReader reader, String field) throws IOException {
        super(weight);
        this.docScorer = docScorer;
        this.postingsEnum = td;
        this.baseScorer = baseScorer;
        this.norms = reader.getNormValues(field);
    }

    @Override
    public int docID() {
        return postingsEnum.docID();
    }

    public final int freq() throws IOException {
        return postingsEnum.freq();
    }

    @Override
    public DocIdSetIterator iterator() {
        return postingsEnum;
    }

    @Override
    public float getMaxScore(int upTo) throws IOException {
        return baseScorer.getMaxScore(upTo);
    }

    @Override
    public float score() throws IOException {
        assert docID() != DocIdSetIterator.NO_MORE_DOCS;
        // get payload here instead?
        return docScorer.score(postingsEnum.freq(), getNormValue(docID()));
    }

    private long getNormValue(int doc) throws IOException {
        if (norms != null) {
            boolean found = norms.advanceExact(doc);
            assert found;
            return norms.longValue();
        } else {
            return 1L; // default norm
        }
    }

    /**
     * Returns a string representation of this <code>TermScorer</code>.
     */
    @Override
    public String toString() {
        return "scorer(" + weight + ")[" + super.toString() + "]";
    }
}
