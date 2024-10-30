package org.example;

import io.streamnative.beam.pulsar.PulsarIO;
import io.streamnative.beam.pulsar.PulsarMessage;
import java.util.Map;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.options.Default;
import org.apache.beam.sdk.options.Description;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SNWordCount {
    private static final Logger LOG = LoggerFactory.getLogger(SNWordCount.class);

    public interface PulsarWordCountOptions extends PipelineOptions {

        @Description("input topic")
        @Default.String("public/default/wordcount-input")
        String getInputTopic();

        void setInputTopic(String value);

        @Description("pulsar cluster")
        @Default.String("")
        String getPulsarCluster();

        void setPulsarCluster(String value);
    }

    public static void main(String[] args) {
        PulsarWordCountOptions options =
                PipelineOptionsFactory.fromArgs(args).withValidation().as(PulsarWordCountOptions.class);
        Pipeline pipeline = Pipeline.create(options);

        Map<String, String> env = System.getenv();
        String clientUrl = env.get("SN_WORKSPACE_PULSAR_SERVICEURL");
        String adminUrl = env.get("SN_WORKSPACE_PULSAR_WEBSERVICEURL");
        String authPlugin = env.get("SN_WORKSPACE_AUTH_PLUGIN");
        String authParams = env.get("SN_WORKSPACE_AUTH_PARAMS");
        if (options.getPulsarCluster() != null && !options.getPulsarCluster().isEmpty()) {
            clientUrl = env.get("SN_WORKSPACE_PULSAR_SERVICEURL_" + options.getPulsarCluster());
            adminUrl = env.get("SN_WORKSPACE_PULSAR_WEBSERVICEURL_" + options.getPulsarCluster());
            authPlugin = env.get("SN_WORKSPACE_AUTH_PLUGIN_" + options.getPulsarCluster());
            authParams = env.get("SN_WORKSPACE_AUTH_PARAMS_" + options.getPulsarCluster());
        }

        pipeline.apply(
                        "Read from Pulsar",
                        PulsarIO.<String>read()
                                .withAdminUrl(adminUrl)
                                .withClientUrl(clientUrl)
                                .withAuthPlugin(authPlugin)
                                .withAuthParameters(authParams)
                                .withSchemaType(SchemaType.STRING)
                                .withPojo(String.class)
                                .withReadTimeout(Duration.standardMinutes(5))
                                .withTopic(options.getInputTopic()))
                .apply(ParDo.of(new DoFn<PulsarMessage<String>, String>() {
                    @ProcessElement
                    public void processElement(ProcessContext c) {
                        PulsarMessage<String> message = c.element();
                        LOG.info("=======get message: {}", message.messageRecord());
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
                                        LOG.info("=======key: {}, value: {}", input.getKey(), input.getValue());
                                        return input.getKey() + ": " + input.getValue();
                                    }
                                }));

        pipeline.run().waitUntilFinish();
    }
}
