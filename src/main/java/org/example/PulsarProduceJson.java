package org.example;

import io.streamnative.beam.pulsar.PulsarIO;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.coders.ByteArrayCoder;
import org.apache.beam.sdk.coders.CustomCoder;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.transforms.Create;
import org.apache.pulsar.client.impl.schema.JSONSchema;
import org.apache.pulsar.common.schema.SchemaType;
import org.checkerframework.checker.initialization.qual.Initialized;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.UnknownKeyFor;

public class PulsarProduceJson {
    @Data
    static class Person {
        String name;
    }

    public static void main(String[] args) {
        PulsarIO.Write<Person> write = PulsarIO.<Person>write()
                .withClientUrl("pulsar://localhost:6650")
                .withTopic("beam-output-json")
                .withSchemaType(SchemaType.JSON)
                .withPojo(Person.class);

        PipelineOptions options = PipelineOptionsFactory.create();
        Pipeline pipeline = Pipeline.create(options);

        List<Person> messages = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Person person = new Person();
            person.setName("hello beam " + i);
            messages.add(person);
        }

        pipeline.apply(Create.of(messages).withCoder(new CustomCoder<>() {
            private static final ByteArrayCoder byteArrayCoder = ByteArrayCoder.of();

            @Override
            public void encode(Person value, @UnknownKeyFor @NonNull @Initialized OutputStream outStream)
                    throws IOException {
                byte[] data = JSONSchema.of(Person.class).encode(value);
                byteArrayCoder.encode(data, outStream);
            }

            @Override
            public Person decode(@UnknownKeyFor @NonNull @Initialized InputStream inStream)
                    throws @UnknownKeyFor @NonNull @Initialized
                    IOException {
                byte[] data = byteArrayCoder.decode(inStream);
                return JSONSchema.of(Person.class).decode(data);
            }
        })).apply(write);
        pipeline.run().waitUntilFinish();
    }

}
