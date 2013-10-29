package com.interopbridges.tools.windowsazure;
/**
 * Copyright 2013 Persistent Systems Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

@RunWith(Suite.class)
@SuiteClasses({WindowsAzureRoleTest.class,
                WindowsAzureEndpointTest.class,
                WindowsAzureProjectManagerTest.class,
                WindowsAzureLocalStorageTest.class,
                WindowsAzureEndpointSATest.class,
                WindowsAzureRoleComponentTest.class,
                WindowsAzureCacheTest.class,
                WindowsAzureRoleDeployFromDownloadTest.class})
public class AllTests {

}
