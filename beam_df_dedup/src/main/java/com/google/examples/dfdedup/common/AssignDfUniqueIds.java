// Copyright 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.examples.dfdedup.common;

import org.apache.beam.sdk.state.StateSpec;
import org.apache.beam.sdk.state.StateSpecs;
import org.apache.beam.sdk.state.ValueState;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.MapElements;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.TypeDescriptor;
import org.apache.beam.sdk.values.TypeDescriptors;

import static com.google.common.base.MoreObjects.firstNonNull;

public class AssignDfUniqueIds extends PTransform<PCollection<Message>, PCollection<Message>> {
    @Override
    public PCollection<Message> expand(PCollection<Message> inMsgs) {
        PCollection<Message> msgsWithIds =
                inMsgs.apply("MapMessagesToRunNameKV",
                        MapElements.into(
                                TypeDescriptors.kvs(TypeDescriptors.strings(), TypeDescriptor.of(Message.class)))
                                .via((Message s) -> KV.of(s.getRunName(), s)))
                .apply("AssignUniqueDfIds", ParDo.of(new IdAssignmentDoFn()))
                .apply("RemoveRunNameKey", MapElements.into(TypeDescriptor.of(Message.class))
                        .via((KV<String, Message> kv) -> (kv.getValue())));

        return msgsWithIds;
    }

    private static class IdAssignmentDoFn extends DoFn<KV<String, Message>, KV<String, Message>> {
        @StateId("count")
        private final StateSpec<ValueState<Integer>> countStateSpec = StateSpecs.value();

        @ProcessElement
        public void processElement(ProcessContext processContext,
                                   @StateId("count") ValueState<Integer> countState) {
            int count = firstNonNull(countState.read(), 1);

            Message m = processContext.element().getValue();
            m.setDfUniqueId(count);
            processContext.output(KV.of(m.RunName, m));
            count += 1;
            countState.write(count);
        }
    }
}