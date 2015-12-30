/*
 * Copyright 2015 GoDataDriven B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.divolte.server;

import com.google.common.collect.ImmutableCollection;
import io.divolte.server.config.ValidatedConfiguration;
import io.divolte.server.js.TrackingJavaScriptResource;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.util.Methods;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.ParametersAreNonnullByDefault;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;

@ParametersAreNonnullByDefault
public class BrowserSource {
    private static final Logger logger = LoggerFactory.getLogger(BrowserSource.class);

    private final String sourceName;
    private final String pathPrefix;
    private final String javascriptName;
    private final HttpHandler javascriptHandler;
    private final HttpHandler eventHandler;

    public BrowserSource(final ValidatedConfiguration vc,
                         final String sourceName,
                         final ImmutableCollection<IncomingRequestProcessingPool> mappingProcessors) {
        this(sourceName,
             vc.configuration().getBrowserSourceConfiguration(sourceName).prefix,
             loadTrackingJavaScript(vc, sourceName),
             mappingProcessors);
    }

    private BrowserSource(final String sourceName,
                          final String pathPrefix,
                          final TrackingJavaScriptResource trackingJavascript,
                          final ImmutableCollection<IncomingRequestProcessingPool> mappingProcessors) {
        this.sourceName = Objects.requireNonNull(sourceName);
        this.pathPrefix = Objects.requireNonNull(pathPrefix);
        javascriptName = trackingJavascript.getScriptName();
        javascriptHandler = new AllowedMethodsHandler(new JavaScriptHandler(trackingJavascript), Methods.GET);
        final EventForwarder<DivolteEvent> processingPoolsForwarder = EventForwarder.create(mappingProcessors);
        final ClientSideCookieEventHandler clientSideCookieEventHandler = new ClientSideCookieEventHandler(processingPoolsForwarder);
        eventHandler = new AllowedMethodsHandler(clientSideCookieEventHandler, Methods.GET);
    }

    public PathHandler attachToPathHandler(PathHandler pathHandler) {
        final String javascriptPath = pathPrefix + javascriptName;
        pathHandler = pathHandler.addExactPath(javascriptPath, javascriptHandler);
        logger.info("Registered source[{}] script location: {}", sourceName, javascriptPath);
        final String eventPath = pathPrefix + "csc-event";
        pathHandler = pathHandler.addExactPath(eventPath, eventHandler);
        logger.info("Registered source[{}] event handler: {}", sourceName, eventPath);
        return pathHandler;
    }

    private static TrackingJavaScriptResource loadTrackingJavaScript(final ValidatedConfiguration vc, final String sourceName) {
        try {
            return TrackingJavaScriptResource.create(vc, sourceName);
        } catch (final IOException e) {
            throw new UncheckedIOException("Could not precompile tracking JavaScript for source: " + sourceName, e);
        }
    }
}