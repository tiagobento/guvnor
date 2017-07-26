/*
 * Copyright 2017 Red Hat, Inc. and/or its affiliates.
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

package org.guvnor.ala.ui.backend.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.guvnor.ala.pipeline.Input;
import org.guvnor.ala.services.api.RuntimeQuery;
import org.guvnor.ala.services.api.RuntimeQueryResultItem;
import org.guvnor.ala.services.api.backend.PipelineServiceBackend;
import org.guvnor.ala.services.api.backend.RuntimeProvisioningServiceBackend;
import org.guvnor.ala.ui.model.InternalGitSource;
import org.guvnor.ala.ui.model.PipelineExecutionTraceKey;
import org.guvnor.ala.ui.model.PipelineKey;
import org.guvnor.ala.ui.model.Provider;
import org.guvnor.ala.ui.model.ProviderKey;
import org.guvnor.ala.ui.model.ProviderTypeKey;
import org.guvnor.ala.ui.model.RuntimeListItem;
import org.guvnor.ala.ui.model.RuntimesInfo;
import org.guvnor.ala.ui.model.Source;
import org.guvnor.ala.ui.service.ProviderService;
import org.guvnor.ala.ui.service.RuntimeService;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.guvnor.ala.ui.ProvisioningManagementTestCommons.ERROR_MESSAGE;
import static org.guvnor.ala.ui.ProvisioningManagementTestCommons.PROVIDER_ID;
import static org.guvnor.ala.ui.ProvisioningManagementTestCommons.PROVIDER_NAME;
import static org.guvnor.ala.ui.ProvisioningManagementTestCommons.PROVIDER_VERSION;
import static org.guvnor.ala.ui.backend.service.RuntimeListItemBuilderTest.mockPipelineStageItemList;
import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class RuntimeServiceImplTest {

    private static final int QUERY_ITEMS_SIZE = 5;

    private static final String RUNTIME_ID = "RUNTIME_ID";

    private static final String OU = "OU";

    private static final String REPOSITORY = "REPOSITORY";

    private static final String BRANCH = "BRANCH";

    private static final String PROJECT = "PROJECT";

    private static final String PIPELINE = "PIPELINE";

    private static final PipelineKey PIPELINE_KEY = new PipelineKey(PIPELINE);

    @Mock
    private RuntimeProvisioningServiceBackend runtimeProvisioningService;

    @Mock
    private PipelineServiceBackend pipelineService;

    @Mock
    private ProviderService providerService;

    private RuntimeService service;

    private List<RuntimeQueryResultItem> queryItems;

    private List<String> pipelineNames;

    private List<PipelineKey> pipelineKeys;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Before
    public void setUp() {

        queryItems = mockRuntimeQueryResultItemList(QUERY_ITEMS_SIZE);
        pipelineNames = mockPipelineNames(QUERY_ITEMS_SIZE);
        pipelineKeys = mockPipelineKeys(pipelineNames);

        service = new RuntimeServiceImpl(runtimeProvisioningService,
                                         pipelineService,
                                         providerService);
    }

    @Test
    public void testGetRuntimeItems() {
        ProviderTypeKey providerTypeKey = new ProviderTypeKey(PROVIDER_NAME,
                                                              PROVIDER_VERSION);
        ProviderKey providerKey = new ProviderKey(providerTypeKey,
                                                  PROVIDER_ID);

        when(runtimeProvisioningService.executeQuery(any(RuntimeQuery.class))).thenReturn(queryItems);

        Collection<RuntimeListItem> result = service.getRuntimeItems(providerKey);
        Collection<RuntimeListItem> expectedResult = buildExpectedResult(queryItems);

        assertEquals(expectedResult,
                     result);
    }

    @Test
    public void getRuntimeItemExisting() {
        String pipelineExecutionId = "executionId";
        PipelineExecutionTraceKey traceKey = new PipelineExecutionTraceKey(pipelineExecutionId);
        List<RuntimeQueryResultItem> singleResult = mockRuntimeQueryResultItemList(1);
        when(runtimeProvisioningService.executeQuery(any(RuntimeQuery.class))).thenReturn(singleResult);
        RuntimeListItem expectedItem = buildExpectedResult(singleResult).iterator().next();
        RuntimeListItem result = service.getRuntimeItem(traceKey);
        assertEquals(expectedItem,
                     result);
    }

    @Test
    public void getRuntimeItemNotExisting() {
        String pipelineExecutionId = "executionId";
        PipelineExecutionTraceKey traceKey = new PipelineExecutionTraceKey(pipelineExecutionId);
        List<RuntimeQueryResultItem> singleResult = new ArrayList<>();
        when(runtimeProvisioningService.executeQuery(any(RuntimeQuery.class))).thenReturn(singleResult);
        RuntimeListItem result = service.getRuntimeItem(traceKey);
        assertNull(result);
    }

    @Test
    public void testGetPipelines() {
        ProviderTypeKey providerTypeKey = new ProviderTypeKey(PROVIDER_NAME,
                                                              PROVIDER_VERSION);
        when(pipelineService.getPipelineNames(any(org.guvnor.ala.runtime.providers.ProviderType.class),
                                              anyInt(),
                                              anyInt(),
                                              anyString(),
                                              anyBoolean())).thenReturn(pipelineNames);
        Collection<PipelineKey> result = service.getPipelines(providerTypeKey);
        assertEquals(pipelineKeys,
                     result);
    }

    @Test
    public void testCreateRuntimeWhenProviderExists() {
        Provider provider = mock(Provider.class);

        ProviderTypeKey providerTypeKey = new ProviderTypeKey(PROVIDER_NAME,
                                                              PROVIDER_VERSION);
        ProviderKey providerKey = new ProviderKey(providerTypeKey,
                                                  PROVIDER_ID);

        InternalGitSource gitSource = new InternalGitSource(OU,
                                                            REPOSITORY,
                                                            BRANCH,
                                                            PROJECT);

        when(providerService.getProvider(providerKey)).thenReturn(provider);

        Input expectedInput = PipelineInputBuilder.newInstance()
                .withProvider(providerKey)
                .withRuntimeName(RUNTIME_ID)
                .withSource(gitSource).build();

        service.createRuntime(providerKey,
                              RUNTIME_ID,
                              gitSource,
                              PIPELINE_KEY);

        verify(pipelineService,
               times(1)).runPipeline(PIPELINE,
                                     expectedInput,
                                     true);
    }

    @Test
    public void testCreateRuntimeWhenProviderNotExists() {
        ProviderTypeKey providerTypeKey = new ProviderTypeKey(PROVIDER_NAME,
                                                              PROVIDER_VERSION);
        ProviderKey providerKey = new ProviderKey(providerTypeKey,
                                                  PROVIDER_ID);

        expectedException.expectMessage("No provider was found for providerKey: " + providerKey);
        service.createRuntime(providerKey,
                              RUNTIME_ID,
                              mock(Source.class),
                              PIPELINE_KEY);

        verify(pipelineService,
               never()).runPipeline(anyString(),
                                    any(Input.class),
                                    eq(true));
    }

    @Test
    public void testCreateRuntimeWhenUnExpectedError() {
        Provider provider = mock(Provider.class);

        ProviderTypeKey providerTypeKey = new ProviderTypeKey(PROVIDER_NAME,
                                                              PROVIDER_VERSION);
        ProviderKey providerKey = new ProviderKey(providerTypeKey,
                                                  PROVIDER_ID);
        when(providerService.getProvider(providerKey)).thenReturn(provider);
        when(pipelineService.runPipeline(anyString(),
                                         any(Input.class),
                                         eq(true))).thenThrow(new RuntimeException(ERROR_MESSAGE));

        expectedException.expectMessage(ERROR_MESSAGE);
        service.createRuntime(providerKey,
                              "irrelevant for the test",
                              mock(Source.class),
                              mock(PipelineKey.class));
    }

    @Test
    public void testGetRuntimesInfoProviderExisting() {
        ProviderTypeKey providerTypeKey = new ProviderTypeKey(PROVIDER_NAME,
                                                              PROVIDER_VERSION);
        ProviderKey providerKey = new ProviderKey(providerTypeKey,
                                                  PROVIDER_ID);
        Provider provider = mock(Provider.class);
        when(providerService.getProvider(providerKey)).thenReturn(provider);
        when(runtimeProvisioningService.executeQuery(any(RuntimeQuery.class))).thenReturn(queryItems);

        RuntimesInfo info = service.getRuntimesInfo(providerKey);
        assertNotNull(info);
        assertEquals(provider,
                     info.getProvider());
        Collection<RuntimeListItem> expectedResult = buildExpectedResult(queryItems);
        assertEquals(expectedResult,
                     info.getRuntimeItems());
    }

    @Test
    public void testGetRuntimesInfoProviderNotExisting() {
        ProviderKey providerKey = mock(ProviderKey.class);
        when(providerService.getProvider(providerKey)).thenReturn(null);
        RuntimesInfo info = service.getRuntimesInfo(providerKey);
        assertNull(info);
        verify(providerService,
               times(1)).getProvider(providerKey);
    }

    private List<RuntimeQueryResultItem> mockRuntimeQueryResultItemList(int count) {
        List<RuntimeQueryResultItem> items = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            items.add(mockRuntimeQueryResultItem(Integer.toString(i),
                                                 i));
        }
        return items;
    }

    private Collection<RuntimeListItem> buildExpectedResult(List<RuntimeQueryResultItem> resultItems) {
        final Collection<RuntimeListItem> result = resultItems.stream()
                .map(item -> RuntimeListItemBuilder.newInstance().withItem(item).build())
                .collect(Collectors.toList());
        return result;
    }

    private RuntimeQueryResultItem mockRuntimeQueryResultItem(String suffix,
                                                              int stageItemsCount) {
        RuntimeQueryResultItem item = new RuntimeQueryResultItem();

        item.setProviderId("RuntimeQueryResultItem.providerId." + suffix);
        item.setProviderTypeName("RuntimeQueryResultItem.providerTypeName." + suffix);
        item.setProviderVersion("RuntimeQueryResultItem.providerVersion." + suffix);

        item.setPipelineId("RuntimeQueryResultItem.pipelineId." + suffix);
        item.setPipelineExecutionId("RuntimeQueryResultItem.pipelineExecutionId." + suffix);
        item.setPipelineStatus("RUNNING");
        item.setPipelineError("RuntimeQueryResultItem.pipelineError." + suffix);

        item.setPipelineStageItems(mockPipelineStageItemList(stageItemsCount));

        item.setRuntimeId("RuntimeQueryResultItem.runtimeId." + suffix);
        item.setRuntimeName("RuntimeQueryResultItem.runtimeName." + suffix);
        item.setRuntimeStatus("RUNNING");
        item.setRuntimeEndpoint("RuntimeQueryResultItem.runtimeEndpoint." + suffix);

        return item;
    }

    private List<String> mockPipelineNames(int count) {
        List<String> pipelines = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            pipelines.add("Pipeline." + Integer.toString(i));
        }
        return pipelines;
    }

    private List<PipelineKey> mockPipelineKeys(List<String> pipelineNames) {
        return pipelineNames.stream().map(PipelineKey::new).collect(Collectors.toList());
    }
}