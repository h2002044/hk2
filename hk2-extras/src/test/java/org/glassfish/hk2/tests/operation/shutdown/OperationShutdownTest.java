/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2015 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package org.glassfish.hk2.tests.operation.shutdown;

import org.glassfish.hk2.api.ProxyCtl;
import org.glassfish.hk2.api.ServiceHandle;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.extras.operation.OperationHandle;
import org.glassfish.hk2.extras.operation.OperationManager;
import org.glassfish.hk2.tests.operation.basic.BasicOperationScope;
import org.glassfish.hk2.tests.operation.basic.BasicOperationScopeContext;
import org.glassfish.hk2.tests.operation.basic.OperationsTest;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author jwells
 *
 */
public class OperationShutdownTest {
    /**
     * Tests that the destroy of ServiceHandle works
     */
    @Test // @org.junit.Ignore
    public void testServiceHandleDestroyWorks() {
        ServiceLocator locator = OperationsTest.createLocator(BasicOperationScopeContext.class,
                Registrar.class,
                PerLookupClassShutdown.class,
                OperationalServiceWithPerLookupService.class);
        
        OperationManager operationManager = locator.getService(OperationManager.class);
        
        OperationHandle<BasicOperationScope> operationHandle = operationManager.createOperation(OperationsTest.BASIC_OPERATION_ANNOTATION);
        operationHandle.resume();
        
        ServiceHandle<OperationalServiceWithPerLookupService> handle =
                locator.getServiceHandle(OperationalServiceWithPerLookupService.class);
        
        OperationalServiceWithPerLookupService parent = handle.getService();
        if (parent instanceof ProxyCtl) {
            parent = (OperationalServiceWithPerLookupService) ((ProxyCtl) parent).__make();
        }
        
        Assert.assertFalse(handle.getService().isClosed());
        
        PerLookupClassShutdown plcs = handle.getService().getPerLookupService();
        Assert.assertNotNull(plcs);
        
        Registrar registrar = locator.getService(Registrar.class);
        Assert.assertFalse(registrar.isShutDown(plcs));
        
        handle.destroy();
        
        Assert.assertTrue(parent.isClosed());
        Assert.assertTrue(registrar.isShutDown(plcs));
        
        operationHandle.closeOperation();
        
    }
    
    /**
     * Tests that the destroy gets called when an operation is closed, even for
     * a factory created service that was proxied
     */
    @Test // @org.junit.Ignore
    public void testFactoryDestructionWorksOnCloseOperation() {
        ServiceLocator locator = OperationsTest.createLocator(BasicOperationScopeContext.class,
                SingletonWithFactoryCreatedService.class,
                OperationScopeFactory.class);
        
        OperationManager operationManager = locator.getService(OperationManager.class);
        
        OperationHandle<BasicOperationScope> operationHandle = operationManager.createOperation(OperationsTest.BASIC_OPERATION_ANNOTATION);
        operationHandle.resume();
        
        SingletonWithFactoryCreatedService singleton = locator.getService(SingletonWithFactoryCreatedService.class);
        CreatedByFactory factoryCreated = singleton.getFactoryCreated();
        Assert.assertTrue(factoryCreated instanceof ProxyCtl);
        
        // Forces underlying creation
        factoryCreated.createMe();
        
        OperationScopeFactory osf = locator.getService(OperationScopeFactory.class);
        
        CreatedByFactory unwrapped = (CreatedByFactory) ((ProxyCtl) factoryCreated).__make();
        
        Assert.assertFalse(osf.hasBeenDestroyed(unwrapped));
        
        operationHandle.closeOperation();
        
        Assert.assertTrue(osf.hasBeenDestroyed(unwrapped));
    }
    
    /**
     * Tests that the destroy gets called when an operation is closed, even for
     * a factory created service that was proxied and created with a ServiceHandle
     */
    @Test // @org.junit.Ignore
    public void testFactoryDestructionWorksOnCloseOperationWithServiceHandle() {
        ServiceLocator locator = OperationsTest.createLocator(BasicOperationScopeContext.class,
                SingletonWithFactoryCreatedService.class,
                OperationScopeFactory.class);
        
        OperationManager operationManager = locator.getService(OperationManager.class);
        
        OperationHandle<BasicOperationScope> operationHandle = operationManager.createOperation(OperationsTest.BASIC_OPERATION_ANNOTATION);
        operationHandle.resume();
        
        ServiceHandle<SingletonWithFactoryCreatedService> singleton = locator.getServiceHandle(SingletonWithFactoryCreatedService.class);
        CreatedByFactory factoryCreated = singleton.getService().getFactoryCreated();
        Assert.assertTrue(factoryCreated instanceof ProxyCtl);
        
        // Forces underlying creation
        factoryCreated.createMe();
        
        OperationScopeFactory osf = locator.getService(OperationScopeFactory.class);
        
        CreatedByFactory unwrapped = (CreatedByFactory) ((ProxyCtl) factoryCreated).__make();
        
        Assert.assertFalse(osf.hasBeenDestroyed(unwrapped));
        
        operationHandle.closeOperation();
        
        Assert.assertTrue(osf.hasBeenDestroyed(unwrapped));
        
        // Ensures doubles do not happen
        singleton.destroy();
    }
    
    /**
     * Tests that the destroy gets called when an operation is closed, even for
     * a factory created service that was not proxied
     */
    @Test // @org.junit.Ignore
    public void testFactoryDestructionWorksOnCloseOperationNotProxied() {
        ServiceLocator locator = OperationsTest.createLocator(BasicOperationScopeContext.class,
                OperationScopeWithFactoryCreatedService.class,
                OperationScopeFactory.class);
        
        OperationManager operationManager = locator.getService(OperationManager.class);
        
        OperationHandle<BasicOperationScope> operationHandle = operationManager.createOperation(OperationsTest.BASIC_OPERATION_ANNOTATION);
        operationHandle.resume();
        
        OperationScopeWithFactoryCreatedService opScoped = locator.getService(OperationScopeWithFactoryCreatedService.class);
        CreatedByFactory factoryCreated = opScoped.getCreatedByFactory();
        Assert.assertFalse(factoryCreated instanceof ProxyCtl);
        
        // Puts it into the create map in the factory
        factoryCreated.createMe();
        
        OperationScopeFactory osf = locator.getService(OperationScopeFactory.class);
        
        Assert.assertFalse(osf.hasBeenDestroyed(factoryCreated));
        
        operationHandle.closeOperation();
        
        Assert.assertTrue(osf.hasBeenDestroyed(factoryCreated));
    }
    
    /**
     * Tests that the destroy gets called when an operation is closed, even for
     * a factory created service that was not proxied and created with ServiceHandle
     */
    @Test // @org.junit.Ignore
    public void testFactoryDestructionWorksOnCloseOperationNotProxiedWithServiceHandle() {
        ServiceLocator locator = OperationsTest.createLocator(BasicOperationScopeContext.class,
                OperationScopeWithFactoryCreatedService.class,
                OperationScopeFactory.class);
        
        OperationManager operationManager = locator.getService(OperationManager.class);
        
        OperationHandle<BasicOperationScope> operationHandle = operationManager.createOperation(OperationsTest.BASIC_OPERATION_ANNOTATION);
        operationHandle.resume();
        
        ServiceHandle<OperationScopeWithFactoryCreatedService> opScoped = locator.getServiceHandle(OperationScopeWithFactoryCreatedService.class);
        CreatedByFactory factoryCreated = opScoped.getService().getCreatedByFactory();
        Assert.assertFalse(factoryCreated instanceof ProxyCtl);
        
        // Puts it into the create map in the factory
        factoryCreated.createMe();
        
        OperationScopeFactory osf = locator.getService(OperationScopeFactory.class);
        
        Assert.assertFalse(osf.hasBeenDestroyed(factoryCreated));
        
        operationHandle.closeOperation();
        
        Assert.assertTrue(osf.hasBeenDestroyed(factoryCreated));
        
        opScoped.destroy();
    }

}
