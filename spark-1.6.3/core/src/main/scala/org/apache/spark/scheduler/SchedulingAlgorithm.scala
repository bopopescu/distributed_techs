/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.scheduler

/**
 * An interface for sort algorithm
 * FIFO: FIFO algorithm between TaskSetManagers
 * FS: FS algorithm between Pools, and FIFO or FS within Pools
 */
private[spark] trait SchedulingAlgorithm {
  def comparator(s1: Schedulable, s2: Schedulable): Boolean
}

/**
  * 首先根据优先级priority排序，然后根据stageid排序，优先级越小、stageid越小，越先执行；
  */
private[spark] class FIFOSchedulingAlgorithm extends SchedulingAlgorithm {
  override def comparator(s1: Schedulable, s2: Schedulable): Boolean = {
    val priority1 = s1.priority
    val priority2 = s2.priority
    var res = math.signum(priority1 - priority2)
    if (res == 0) {
      val stageId1 = s1.stageId
      val stageId2 = s2.stageId
      res = math.signum(stageId1 - stageId2)
    }
    if (res < 0) {
      true //priority越小，stageid越小，TaskSetManger的优先级越大
    } else {
      false
    }
  }
}

/**
  * 公平调度是一个树结构，因此其优先级比较也复杂点
  * 运行Task越多则说明越重要，优先级也就越高
  *
  * FairSchedulingAlgorithm算法策略是防止防止饥饿现象，因此根据minShare与runningTasks的关系分为以下情况：
  * （1）若两个调度实体中有一个满足runningTasks < minShare，为防止饥饿，让对应的调度实体先执行；
  * （2）若两个调度实体中都满足runningTasks < minShare，则比较runningTasks/minShare的关系，那个比值小先执行那个（任务获取COREs少的调度实体）；
  * （3）若两个调度实体中都不满足runningTasks < minShare，则比较runningTasks/weight的关系，那个比值小先执行那个（任务权重小的调度实体）；
  * （4）若以上都不满足，则根据name判断调度的优先级；
  */
private[spark] class FairSchedulingAlgorithm extends SchedulingAlgorithm {
  override def comparator(s1: Schedulable, s2: Schedulable): Boolean = {
    val minShare1 = s1.minShare
    val minShare2 = s2.minShare
    val runningTasks1 = s1.runningTasks
    val runningTasks2 = s2.runningTasks
    val s1Needy = runningTasks1 < minShare1
    val s2Needy = runningTasks2 < minShare2
    val minShareRatio1 = runningTasks1.toDouble / math.max(minShare1, 1.0).toDouble
    val minShareRatio2 = runningTasks2.toDouble / math.max(minShare2, 1.0).toDouble
    val taskToWeightRatio1 = runningTasks1.toDouble / s1.weight.toDouble
    val taskToWeightRatio2 = runningTasks2.toDouble / s2.weight.toDouble
    var compare: Int = 0

    if (s1Needy && !s2Needy) { //S1中正在执行的Task个数小于minShare最小CPU核数，则S2中的相反，则让S1的优先级高点先被调度，防止饥饿现象
      return true
    } else if (!s1Needy && s2Needy) {
      return false
    } else if (s1Needy && s2Needy) { //
      compare = minShareRatio1.compareTo(minShareRatio2)
    } else { //
      compare = taskToWeightRatio1.compareTo(taskToWeightRatio2)
    }

    if (compare < 0) {
      true
    } else if (compare > 0) {
      false
    } else {
      //最后根据名字判断优先级
      s1.name < s2.name
    }
  }
}

