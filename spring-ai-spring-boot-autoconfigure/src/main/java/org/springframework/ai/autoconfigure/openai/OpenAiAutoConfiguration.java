/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.autoconfigure.openai;

import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.springframework.ai.autoconfigure.retry.SpringAiRetryAutoConfiguration;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallbackContext;
import org.springframework.ai.openai.OpenAiAudioSpeechModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiImageModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.OpenAiAudioApi;
import org.springframework.ai.openai.api.OpenAiImageApi;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * @author Christian Tzolov
 * @author Stefan Vassilev
 * @author Thomas Vitale
 */
@AutoConfiguration(after = { RestClientAutoConfiguration.class, WebClientAutoConfiguration.class,
		SpringAiRetryAutoConfiguration.class })
@ConditionalOnClass(OpenAiApi.class)
@EnableConfigurationProperties({ OpenAiConnectionProperties.class, OpenAiChatProperties.class,
		OpenAiEmbeddingProperties.class, OpenAiImageProperties.class, OpenAiAudioTranscriptionProperties.class,
		OpenAiAudioSpeechProperties.class })
@ImportAutoConfiguration(classes = { SpringAiRetryAutoConfiguration.class, RestClientAutoConfiguration.class,
		WebClientAutoConfiguration.class })
public class OpenAiAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = OpenAiChatProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
			matchIfMissing = true)
	public OpenAiChatModel openAiChatModel(OpenAiConnectionProperties commonProperties,
			OpenAiChatProperties chatProperties, RestClient.Builder restClientBuilder,
			WebClient.Builder webClientBuilder, List<FunctionCallback> toolFunctionCallbacks,
			FunctionCallbackContext functionCallbackContext, RetryTemplate retryTemplate,
			ResponseErrorHandler responseErrorHandler) {

		var openAiApi = openAiApi(chatProperties, commonProperties, restClientBuilder, webClientBuilder,
				responseErrorHandler, "chat");

		if (!CollectionUtils.isEmpty(toolFunctionCallbacks)) {
			chatProperties.getOptions().getFunctionCallbacks().addAll(toolFunctionCallbacks);
		}

		return new OpenAiChatModel(openAiApi, chatProperties.getOptions(), functionCallbackContext, retryTemplate);
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = OpenAiEmbeddingProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
			matchIfMissing = true)
	public OpenAiEmbeddingModel openAiEmbeddingModel(OpenAiConnectionProperties commonProperties,
			OpenAiEmbeddingProperties embeddingProperties, RestClient.Builder restClientBuilder,
			WebClient.Builder webClientBuilder, RetryTemplate retryTemplate,
			ResponseErrorHandler responseErrorHandler) {

		var openAiApi = openAiApi(embeddingProperties, commonProperties, restClientBuilder, webClientBuilder,
				responseErrorHandler, "embedding");

		return new OpenAiEmbeddingModel(openAiApi, embeddingProperties.getMetadataMode(),
				embeddingProperties.getOptions(), retryTemplate);
	}

	private OpenAiApi openAiApi(OpenAiChatProperties chatProperties, OpenAiConnectionProperties commonProperties,
			RestClient.Builder restClientBuilder, WebClient.Builder webClientBuilder,
			ResponseErrorHandler responseErrorHandler, String modelType) {
		ResolvedBaseUrlAndApiKey result = getResolvedBaseUrlAndApiKey(chatProperties.getBaseUrl(),
				chatProperties.getApiKey(), commonProperties, modelType);

		return new OpenAiApi(result.resolvedBaseUrl(), result.resolvedApiKey(), chatProperties.getCompletionsPath(),
				OpenAiEmbeddingProperties.DEFAULT_EMBEDDINGS_PATH, restClientBuilder, webClientBuilder,
				responseErrorHandler);
	}

	private OpenAiApi openAiApi(OpenAiEmbeddingProperties embeddingProperties,
			OpenAiConnectionProperties commonProperties, RestClient.Builder restClientBuilder,
			WebClient.Builder webClientBuilder, ResponseErrorHandler responseErrorHandler, String modelType) {
		ResolvedBaseUrlAndApiKey result = getResolvedBaseUrlAndApiKey(embeddingProperties.getBaseUrl(),
				embeddingProperties.getApiKey(), commonProperties, modelType);

		return new OpenAiApi(result.resolvedBaseUrl(), result.resolvedApiKey(),
				OpenAiChatProperties.DEFAULT_COMPLETIONS_PATH, embeddingProperties.getEmbeddingsPath(),
				restClientBuilder, webClientBuilder, responseErrorHandler);
	}

	private static @NotNull ResolvedBaseUrlAndApiKey getResolvedBaseUrlAndApiKey(String baseUrl, String apiKey,
			OpenAiConnectionProperties commonProperties, String modelType) {
		var commonBaseUrl = commonProperties.getBaseUrl();
		var commonApiKey = commonProperties.getApiKey();

		String resolvedBaseUrl = StringUtils.hasText(baseUrl) ? baseUrl : commonBaseUrl;
		Assert.hasText(resolvedBaseUrl,
				"OpenAI base URL must be set.  Use the connection property: spring.ai.openai.base-url or spring.ai.openai."
						+ modelType + ".base-url property.");

		String resolvedApiKey = StringUtils.hasText(apiKey) ? apiKey : commonApiKey;
		Assert.hasText(resolvedApiKey,
				"OpenAI API key must be set. Use the connection property: spring.ai.openai.api-key or spring.ai.openai."
						+ modelType + ".api-key property.");
		return new ResolvedBaseUrlAndApiKey(resolvedBaseUrl, resolvedApiKey);
	}

	private record ResolvedBaseUrlAndApiKey(String resolvedBaseUrl, String resolvedApiKey) {
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = OpenAiImageProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
			matchIfMissing = true)
	public OpenAiImageModel openAiImageModel(OpenAiConnectionProperties commonProperties,
			OpenAiImageProperties imageProperties, RestClient.Builder restClientBuilder, RetryTemplate retryTemplate,
			ResponseErrorHandler responseErrorHandler) {

		String apiKey = StringUtils.hasText(imageProperties.getApiKey()) ? imageProperties.getApiKey()
				: commonProperties.getApiKey();

		String baseUrl = StringUtils.hasText(imageProperties.getBaseUrl()) ? imageProperties.getBaseUrl()
				: commonProperties.getBaseUrl();

		Assert.hasText(apiKey,
				"OpenAI API key must be set.  Use the property: spring.ai.openai.api-key or spring.ai.openai.image.api-key property.");
		Assert.hasText(baseUrl,
				"OpenAI base URL must be set.  Use the property: spring.ai.openai.base-url or spring.ai.openai.image.base-url property.");

		var openAiImageApi = new OpenAiImageApi(baseUrl, apiKey, restClientBuilder, responseErrorHandler);

		return new OpenAiImageModel(openAiImageApi, imageProperties.getOptions(), retryTemplate);
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = OpenAiAudioTranscriptionProperties.CONFIG_PREFIX, name = "enabled",
			havingValue = "true", matchIfMissing = true)
	public OpenAiAudioTranscriptionModel openAiAudioTranscriptionModel(OpenAiConnectionProperties commonProperties,
			OpenAiAudioTranscriptionProperties transcriptionProperties, RetryTemplate retryTemplate,
			RestClient.Builder restClientBuilder, WebClient.Builder webClientBuilder,
			ResponseErrorHandler responseErrorHandler) {

		String apiKey = StringUtils.hasText(transcriptionProperties.getApiKey()) ? transcriptionProperties.getApiKey()
				: commonProperties.getApiKey();

		String baseUrl = StringUtils.hasText(transcriptionProperties.getBaseUrl())
				? transcriptionProperties.getBaseUrl() : commonProperties.getBaseUrl();

		Assert.hasText(apiKey,
				"OpenAI API key must be set.  Use the property: spring.ai.openai.api-key or spring.ai.openai.audio.transcription.api-key property.");
		Assert.hasText(baseUrl,
				"OpenAI base URL must be set.  Use the property: spring.ai.openai.base-url or spring.ai.openai.audio.transcription.base-url property.");

		var openAiAudioApi = new OpenAiAudioApi(baseUrl, apiKey, restClientBuilder, webClientBuilder,
				responseErrorHandler);

		return new OpenAiAudioTranscriptionModel(openAiAudioApi, transcriptionProperties.getOptions(), retryTemplate);

	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = OpenAiAudioSpeechProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
			matchIfMissing = true)
	public OpenAiAudioSpeechModel openAiAudioSpeechClient(OpenAiConnectionProperties commonProperties,
			OpenAiAudioSpeechProperties speechProperties, RetryTemplate retryTemplate,
			RestClient.Builder restClientBuilder, WebClient.Builder webClientBuilder,
			ResponseErrorHandler responseErrorHandler) {

		String apiKey = StringUtils.hasText(speechProperties.getApiKey()) ? speechProperties.getApiKey()
				: commonProperties.getApiKey();

		String baseUrl = StringUtils.hasText(speechProperties.getBaseUrl()) ? speechProperties.getBaseUrl()
				: commonProperties.getBaseUrl();

		Assert.hasText(apiKey,
				"OpenAI API key must be set.  Use the property: spring.ai.openai.api-key or spring.ai.openai.audio.speech.api-key property.");
		Assert.hasText(baseUrl,
				"OpenAI base URL must be set.  Use the property: spring.ai.openai.base-url or spring.ai.openai.audio.speech.base-url property.");

		var openAiAudioApi = new OpenAiAudioApi(baseUrl, apiKey, restClientBuilder, webClientBuilder,
				responseErrorHandler);

		return new OpenAiAudioSpeechModel(openAiAudioApi, speechProperties.getOptions());
	}

	@Bean
	@ConditionalOnMissingBean
	public FunctionCallbackContext springAiFunctionManager(ApplicationContext context) {
		FunctionCallbackContext manager = new FunctionCallbackContext();
		manager.setApplicationContext(context);
		return manager;
	}

}
