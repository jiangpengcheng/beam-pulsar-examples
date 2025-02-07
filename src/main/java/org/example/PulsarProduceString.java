package org.example;

import io.streamnative.beam.pulsar.PulsarIO;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.transforms.Create;
import org.apache.beam.sdk.values.PCollection;
import org.apache.pulsar.common.schema.SchemaType;

public class PulsarProduceString {

    public static void main(String[] args) {
        PulsarIO.Write<String> write = PulsarIO.<String>write()
                .withClientUrl("pulsar://localhost:6650")
                .withTopic("beam-output-string")
                .withSchemaType(SchemaType.STRING)
                .withPojo(String.class);

        PipelineOptions options = PipelineOptionsFactory.create();
        Pipeline pipeline = Pipeline.create(options);

        PCollection<String> input = pipeline.apply(Create.of("Hello", "Beam", "Pulsar"));
        input.apply(write);

        pipeline.run().waitUntilFinish();
    }

}
