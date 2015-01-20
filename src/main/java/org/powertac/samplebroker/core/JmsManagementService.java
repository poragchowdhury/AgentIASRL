/*
 * Copyright (c) 2012 by the original author
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.powertac.samplebroker.core;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

import javax.annotation.Resource;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageListener;
import javax.jms.Session;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.pool.PooledConnectionFactory;
import org.apache.log4j.Logger;
import org.powertac.common.config.ConfigurableValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.connection.CachingConnectionFactory;
import org.springframework.jms.listener.AbstractMessageListenerContainer;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.stereotype.Service;

/**
 * 
 * @author Nguyen Nguyen, John Collins
 */
@Service
public class JmsManagementService {
  static private Logger log = Logger.getLogger(JmsManagementService.class);

  @Resource(name="jmsFactory")
  private ConnectionFactory connectionFactory;
  
  @Autowired
  private Executor taskExecutor;
  
  @Autowired
  private BrokerPropertiesService brokerPropertiesService;
  
  // configurable parameters
  private String serverQueueName = "serverInput"; 
  private String jmsBrokerUrl = "tcp://localhost:61616";
  
  // JMS artifacts
  Connection connection;
  boolean connectionOpen = false;
  DefaultMessageListenerContainer container;
  Session session;

  private Map<MessageListener,AbstractMessageListenerContainer> listenerContainerMap = 
      new HashMap<MessageListener,AbstractMessageListenerContainer>();
  
  public void init (String overridenBrokerUrl,
                    String serverQueueName)
  {
    brokerPropertiesService.configureMe(this);
    this.serverQueueName = serverQueueName; 
    if (overridenBrokerUrl != null && !overridenBrokerUrl.isEmpty()) {
      setJmsBrokerUrl(overridenBrokerUrl);
    }
    
    ActiveMQConnectionFactory amqConnectionFactory = null;
    if (connectionFactory instanceof PooledConnectionFactory) {
      PooledConnectionFactory pooledConnectionFactory = (PooledConnectionFactory) connectionFactory;
      if (pooledConnectionFactory.getConnectionFactory() instanceof ActiveMQConnectionFactory) {
        amqConnectionFactory = (ActiveMQConnectionFactory) pooledConnectionFactory
            .getConnectionFactory();
      }
    }
    else if (connectionFactory instanceof CachingConnectionFactory) {
      CachingConnectionFactory cachingConnectionFactory = (CachingConnectionFactory) connectionFactory;
      if (cachingConnectionFactory.getTargetConnectionFactory() instanceof ActiveMQConnectionFactory) {
        amqConnectionFactory = (ActiveMQConnectionFactory) cachingConnectionFactory
            .getTargetConnectionFactory();
      }
    }

    if (amqConnectionFactory != null) {
      amqConnectionFactory.setBrokerURL(getJmsBrokerUrl());
    }
  }
  
  public void registerMessageListener(MessageListener listener,
                                      String destinationName)
  {
    //log.info("registerMessageListener(" + destinationName + ", " + listener + ")");
    container = new DefaultMessageListenerContainer();
    container.setConnectionFactory(connectionFactory);
    container.setDestinationName(destinationName);
    container.setMessageListener(listener);
    container.setTaskExecutor(taskExecutor);
    container.afterPropertiesSet();
    container.start();
    
    listenerContainerMap.put(listener, container);
  }

  public synchronized void shutdown ()
  {
    Runnable callback = new Runnable() {
      @Override
      public void run ()
      {
        closeConnection();
      }
    };
    container.stop(callback);
    
    while (connectionOpen) {
      try {
        wait();
      }
      catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }

  private synchronized void closeConnection ()
  {
    //session.close();
    //connection.close();
    connectionOpen = false;
    notifyAll();
  }
  
  public String getServerQueueName()
  {
    return serverQueueName;
  }
  /**
   * @param serverQueueName the serverQueueName to set
   */
  public void setServerQueueName (String serverQueueName)
  {
    this.serverQueueName = serverQueueName;
  }  
  /**
   * @return the jmsBrokerUrl
   */
  public String getJmsBrokerUrl ()
  {
    return jmsBrokerUrl;
  }

  /**
   * @param jmsBrokerUrl the jmsBrokerUrl to set
   */
  @ConfigurableValue(valueType = "String",
          description = "JMS broker URL to use")  
  public void setJmsBrokerUrl (String jmsBrokerUrl)
  {
    this.jmsBrokerUrl = jmsBrokerUrl;
  }  
}
