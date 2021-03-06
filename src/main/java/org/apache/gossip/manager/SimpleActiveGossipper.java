/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.gossip.manager;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.gossip.LocalGossipMember;

import com.codahale.metrics.MetricRegistry;

/**
 * Base implementation gossips randomly to live nodes periodically gossips to dead ones
 *
 */
public class SimpleActiveGossipper extends AbstractActiveGossiper {

  private ScheduledExecutorService scheduledExecutorService;
  private final BlockingQueue<Runnable> workQueue;
  private ThreadPoolExecutor threadService;
  private final Random random;
  
  public SimpleActiveGossipper(GossipManager gossipManager, GossipCore gossipCore,
          MetricRegistry registry) {
    super(gossipManager, gossipCore, registry);
    scheduledExecutorService = Executors.newScheduledThreadPool(2);
    workQueue = new ArrayBlockingQueue<Runnable>(1024);
    threadService = new ThreadPoolExecutor(1, 30, 1, TimeUnit.SECONDS, workQueue,
            new ThreadPoolExecutor.DiscardOldestPolicy());
    random = new Random();
  }

  @Override
  public void init() {
    super.init();
    scheduledExecutorService.scheduleAtFixedRate(() -> {
      threadService.execute(() -> {
        sendToALiveMember();
      });
    }, 0, gossipManager.getSettings().getGossipInterval(), TimeUnit.MILLISECONDS);
    scheduledExecutorService.scheduleAtFixedRate(() -> {
      sendToDeadMember();
    }, 0, gossipManager.getSettings().getGossipInterval(), TimeUnit.MILLISECONDS);
    scheduledExecutorService.scheduleAtFixedRate(
            () -> sendPerNodeData(gossipManager.getMyself(),
                    selectPartner(gossipManager.getLiveMembers())),
            0, gossipManager.getSettings().getGossipInterval(), TimeUnit.MILLISECONDS);
    scheduledExecutorService.scheduleAtFixedRate(
            () -> sendSharedData(gossipManager.getMyself(),
                    selectPartner(gossipManager.getLiveMembers())),
            0, gossipManager.getSettings().getGossipInterval(), TimeUnit.MILLISECONDS);
  }

  @Override
  public void shutdown() {
    super.shutdown();
    scheduledExecutorService.shutdown();
    try {
      scheduledExecutorService.awaitTermination(5, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      LOGGER.debug("Issue during shutdown", e);
    }
  }

  protected void sendToALiveMember(){
    LocalGossipMember member = selectPartner(gossipManager.getLiveMembers());
    sendMembershipList(gossipManager.getMyself(), member);
  }
  
  protected void sendToDeadMember(){
    LocalGossipMember member = selectPartner(gossipManager.getDeadMembers());
    sendMembershipList(gossipManager.getMyself(), member);
  }
  
  /**
   * 
   * @param memberList
   *          The list of members which are stored in the local list of members.
   * @return The chosen LocalGossipMember to gossip with.
   */
  protected LocalGossipMember selectPartner(List<LocalGossipMember> memberList) {
    //TODO this selection is racey what if the list size changes?
    LocalGossipMember member = null;
    if (memberList.size() > 0) {
      int randomNeighborIndex = random.nextInt(memberList.size());
      member = memberList.get(randomNeighborIndex);
    }
    return member;
  }
}
