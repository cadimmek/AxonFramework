/*
 * Copyright (c) 2010-2018. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.deadline;

import org.axonframework.commandhandling.model.Repository;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.config.Configuration;
import org.axonframework.config.SagaConfiguration;
import org.axonframework.deadline.quartz.QuartzDeadlineManager;
import org.axonframework.eventhandling.saga.AbstractSagaManager;
import org.axonframework.eventhandling.saga.AnnotatedSagaManager;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.StdSchedulerFactory;

import java.util.Arrays;
import java.util.function.Function;

public class QuartzDeadlineManagerTest extends AbstractDeadlineManagerTestSuite {

    @Override
    public DeadlineManager buildDeadlineManager(Configuration configuration) {
        Repository aggregateRepository = configuration.repository(MyAggregate.class);
        AbstractSagaManager sagaManager =
                configuration.getModules().stream().filter(m -> m instanceof SagaConfiguration)
                             .map(m -> (SagaConfiguration) m)
                             .filter(sc -> sc.getSagaType().equals(MySaga.class))
                             .map((Function<SagaConfiguration, AnnotatedSagaManager>) SagaConfiguration::getSagaManager)
                             .findAny()
                             .orElseThrow(() -> new IllegalStateException(String.format(
                                     "Setup of %s test class failed, as the SagaConfiguration which is to be expected couldn't be found",
                                     this.getClass().getSimpleName()
                             )));

        try {
            Scheduler scheduler = new StdSchedulerFactory().getScheduler();
            // TODO provide ScopeAwareProvider for tests
            QuartzDeadlineManager quartzDeadlineManager =
                    new QuartzDeadlineManager(scheduler, null/*Arrays.asList(aggregateRepository, sagaManager)*/);
            scheduler.start();
            return quartzDeadlineManager;
        } catch (SchedulerException e) {
            throw new AxonConfigurationException("Unable to configure quartz scheduler", e);
        }
    }
}
