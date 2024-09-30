package org.example;

import io.streamnative.beam.pulsar.PulsarIO;
import io.streamnative.beam.pulsar.PulsarMessage;
import org.apache.beam.runners.direct.DirectRunner;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.transforms.Count;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.MapElements;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.transforms.SimpleFunction;
import org.apache.beam.sdk.transforms.windowing.AfterWatermark;
import org.apache.beam.sdk.transforms.windowing.FixedWindows;
import org.apache.beam.sdk.transforms.windowing.Window;
import org.apache.beam.sdk.values.KV;
import org.apache.pulsar.common.schema.SchemaType;
import org.joda.time.Duration;

public class PulsarWordCount {
    public static void main(String[] args) {
        PipelineOptions options = PipelineOptionsFactory.create();
        options.setRunner(DirectRunner.class);
        Pipeline pipeline = Pipeline.create(options);
        String adminUrl = "http://localhost:8080";
        String clientUrl = "pulsar://localhost:6650";

        pipeline.apply(
                        "Read from Pulsar",
                        PulsarIO.<String>read()
                                .withAdminUrl(adminUrl)
                                .withClientUrl(clientUrl)
                                .withSchemaType(SchemaType.STRING)
                                .withPojo(String.class)
                                .withTopic("word-count"))
                .apply(ParDo.of(new DoFn<PulsarMessage<String>, String>() {
                    @ProcessElement
                    public void processElement(ProcessContext c) {
                        PulsarMessage<String> message = c.element();
                        System.out.println("get message: " + message.messageRecord());
                        for (String word: message.messageRecord().split(" ")) {
                            c.output(word);
                        }
                    }}))
                .apply(
                        Window.<String>into(FixedWindows.of(Duration.standardMinutes(1)))
                                .triggering(AfterWatermark.pastEndOfWindow())
                                .withAllowedLateness(Duration.ZERO)
                                .accumulatingFiredPanes())
                .apply(Count.perElement())
                .apply(
                        "FormatResults",
                        MapElements.via(
                                new SimpleFunction<KV<String, Long>, String>() {
                                    @Override
                                    public String apply(KV<String, Long> input) {
                                        System.out.printf("key: %s, value: %d%n", input.getKey(), input.getValue());
                                        return input.getKey() + ": " + input.getValue();
                                    }
                                }));

        pipeline.run().waitUntilFinish();
    }
}
