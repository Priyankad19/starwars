package org.starwars;

import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import au.com.dius.pact.model.BodyMismatch;
import au.com.dius.pact.model.BodyTypeMismatch;
import au.com.dius.pact.model.HeaderMismatch;
import au.com.dius.pact.model.RequestResponseInteraction;
import au.com.dius.pact.model.Response;
import au.com.dius.pact.model.ResponseMatching$;
import au.com.dius.pact.model.ResponsePartMismatch;
import au.com.dius.pact.model.StatusMismatch;
import au.com.dius.pact.provider.ProviderClient;
import au.com.dius.pact.provider.ProviderInfo;
import au.com.dius.pact.provider.junit.TargetRequestFilter;
import au.com.dius.pact.provider.junit.target.TestClassAwareTarget;
import org.apache.http.HttpRequest;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.TestClass;
import scala.collection.Seq;

public class UrlHttpTarget implements TestClassAwareTarget {


    private final String host;
    private final int port;
    private final String protocol;
    private final String path;
    private TestClass testClass;
    private Object testTarget;

    /**
     * @param host host of tested service
     * @param port port of tested service
     */
    public UrlHttpTarget(final String host, final int port) {
        this("http", host, port);
    }

    /**
     * Host of tested service is assumed as "localhost"
     *
     * @param port port of tested service
     */
    public UrlHttpTarget(final int port) {
        this("http", "localhost", port, null);
    }

    /**
     * @param host     host of tested service
     * @param port     port of tested service
     * @param protocol of tested service
     */
    public UrlHttpTarget(final String protocol, final String host, final int port) {
        this(protocol, host, port, null);
    }

    /**
     * @param host     host of tested service
     * @param port     port of tested service
     * @param protocol of tested service
     * @param path     of the service
     */
    public UrlHttpTarget(final String protocol, final String host, final int port, final String path) {
        this.host = host;
        this.port = port;
        this.protocol = protocol;
        this.path = path;
    }

    public UrlHttpTarget(final URL url) {
        this(url.getProtocol() == null ? "http" : url.getProtocol(),
                url.getHost(),
                url.getPort() == -1 ? 8080 : url.getPort(),
                url.getPath() == null ? "/" : url.getPath());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testInteraction(final RequestResponseInteraction interaction) {
        final ProviderClient providerClient = new ProviderClient();
        providerClient.setProvider(getProviderInfo());
        providerClient.setRequest(interaction.getRequest());
        final Map<String, Object> actualResponse = (Map<String, Object>) providerClient.makeRequest();

        final Seq<ResponsePartMismatch> mismatches = ResponseMatching$.MODULE$.responseMismatches(
                interaction.getResponse(),
                new Response(
                        ((Integer) actualResponse.get("statusCode")).intValue(),
                        (Map<String, String>) actualResponse.get("headers"),
                        (String) actualResponse.get("data"))
        );

        if (!mismatches.isEmpty()) {
            throw getAssertionError(mismatches);
        }
    }

    private ProviderInfo getProviderInfo() {
        final ProviderInfo providerInfo = new ProviderInfo();
        providerInfo.setPort(port);
        providerInfo.setHost(host);
        providerInfo.setProtocol(protocol);
        providerInfo.setPath(path);

        final List<FrameworkMethod> methods = testClass.getAnnotatedMethods(TargetRequestFilter.class);
        if (testClass != null && !methods.isEmpty()) {
            providerInfo.setRequestFilter((Consumer<HttpRequest>) httpRequest -> methods.forEach(method -> {
                try {
                    method.invokeExplosively(testTarget, httpRequest);
                } catch (Throwable t) {
                    throw new AssertionError("Request filter method " + method.getName() + " failed with an exception", t);
                }
            }));
        }

        return providerInfo;
    }

    private AssertionError getAssertionError(final Seq<ResponsePartMismatch> mismatches) {
        final StringBuilder result = new StringBuilder();
        scala.collection.JavaConversions.seqAsJavaList(mismatches)
                .stream()
                .map(
                        mismatch -> {
                            if (mismatch instanceof StatusMismatch) {
                                final StatusMismatch statusMismatch = (StatusMismatch) mismatch;
                                return "StatusMismatch - Expected status " + statusMismatch.expected() + " but was " + statusMismatch.actual();
                            } else if (mismatch instanceof HeaderMismatch) {
                                return ((HeaderMismatch) mismatch).description();
                            } else if (mismatch instanceof BodyTypeMismatch) {
                                final BodyTypeMismatch bodyTypeMismatch = (BodyTypeMismatch) mismatch;
                                return "BodyTypeMismatch - Expected body to have type '" + bodyTypeMismatch.expected() + "' but was '" + bodyTypeMismatch.actual() + "'";
                            } else if (mismatch instanceof BodyMismatch) {
                                return ((BodyMismatch) mismatch).description();
                            } else {
                                return mismatch.toString();
                            }
                        }
                ).forEach(mismatch -> result.append(System.lineSeparator()).append(mismatch));
        return new AssertionError(result.toString());
    }

    @Override
    public void setTestClass(final TestClass testClass, final Object testTarget) {
        this.testClass = testClass;
        this.testTarget = testTarget;
    }
}
