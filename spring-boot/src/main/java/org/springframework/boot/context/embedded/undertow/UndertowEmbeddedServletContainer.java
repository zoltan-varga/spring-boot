/*
 * Copyright 2012-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.context.embedded.undertow;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.Undertow.Builder;
import io.undertow.server.HttpHandler;
import io.undertow.servlet.api.DeploymentManager;

import java.lang.reflect.Field;
import java.util.List;

import javax.servlet.ServletException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.context.embedded.EmbeddedServletContainer;
import org.springframework.boot.context.embedded.EmbeddedServletContainerException;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

/**
 * {@link EmbeddedServletContainer} that can be used to control an embedded Undertow
 * server. Typically this class should be created using
 * {@link UndertowEmbeddedServletContainerFactory} and not directly.
 *
 * @author Ivan Sopov
 * @author Andy Wilkinson
 * @since 1.2.0
 * @see UndertowEmbeddedServletContainer
 */
public class UndertowEmbeddedServletContainer implements EmbeddedServletContainer {

	private static final Log logger = LogFactory
			.getLog(UndertowEmbeddedServletContainer.class);

	private final Builder builder;

	private final DeploymentManager manager;

	private final String contextPath;

	private final int port;

	private final boolean autoStart;

	private Undertow undertow;

	private boolean started = false;

	public UndertowEmbeddedServletContainer(Builder builder, DeploymentManager manager,
			String contextPath, int port, boolean autoStart) {
		this.builder = builder;
		this.manager = manager;
		this.contextPath = contextPath;
		this.port = port;
		this.autoStart = autoStart;
	}

	@Override
	public synchronized void start() throws EmbeddedServletContainerException {
		if (!this.autoStart) {
			return;
		}
		if (this.undertow == null) {
			this.undertow = createUndertowServer();
		}
		this.undertow.start();
		this.started = true;
		UndertowEmbeddedServletContainer.logger.info("Undertow started on port(s) "
				+ getPortsDescription());
	}

	private Undertow createUndertowServer() {
		try {
			HttpHandler servletHandler = this.manager.start();
			this.builder.setHandler(getContextHandler(servletHandler));
			return this.builder.build();
		}
		catch (ServletException ex) {
			throw new EmbeddedServletContainerException(
					"Unable to start embdedded Undertow", ex);
		}
	}

	private HttpHandler getContextHandler(HttpHandler servletHandler) {
		if (StringUtils.isEmpty(this.contextPath)) {
			return servletHandler;
		}
		return Handlers.path().addPrefixPath(this.contextPath, servletHandler);
	}

	@SuppressWarnings("rawtypes")
	private String getPortsDescription() {
		try {
			// Use reflection if possible to get the actual listener configuration
			Field listenersField = ReflectionUtils.findField(Undertow.class, "listeners");
			listenersField.setAccessible(true);
			List listeners = (List) listenersField.get(this.undertow);
			StringBuilder ports = new StringBuilder();
			for (Object listener : listeners) {
				Field portField = ReflectionUtils.findField(listener.getClass(), "port");
				portField.setAccessible(true);
				Field typeField = ReflectionUtils.findField(listener.getClass(), "type");
				typeField.setAccessible(true);
				ports.append(ports.length() == 0 ? "" : ", ");
				ports.append(portField.get(listener) + " ("
						+ typeField.get(listener).toString().toLowerCase() + ")");
			}
			return ports.toString();
		}
		catch (Exception ex) {
		}
		// We at least know our port
		return String.valueOf(this.port);
	}

	@Override
	public synchronized void stop() throws EmbeddedServletContainerException {
		if (this.started) {
			this.started = false;
			this.undertow.stop();
		}
	}

	@Override
	public int getPort() {
		return this.port;
	}

}
