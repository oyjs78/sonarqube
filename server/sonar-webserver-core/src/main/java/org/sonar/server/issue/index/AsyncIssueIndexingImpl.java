/*
 * SonarQube
 * Copyright (C) 2009-2020 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.server.issue.index;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.sonar.api.server.ServerSide;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.ce.queue.CeQueue;
import org.sonar.ce.queue.CeTaskSubmit;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.ce.CeActivityDto;
import org.sonar.db.ce.CeQueueDto;
import org.sonar.db.component.BranchDto;
import org.sonar.db.component.BranchType;
import org.sonar.db.component.SnapshotDto;

import static org.sonar.db.ce.CeTaskCharacteristicDto.BRANCH_KEY;
import static org.sonar.db.ce.CeTaskCharacteristicDto.BRANCH_TYPE_KEY;
import static org.sonar.db.ce.CeTaskCharacteristicDto.PULL_REQUEST;
import static org.sonar.db.ce.CeTaskTypes.BRANCH_ISSUE_SYNC;

@ServerSide
public class AsyncIssueIndexingImpl implements AsyncIssueIndexing {

  private static final Logger LOG = Loggers.get(AsyncIssueIndexingImpl.class);

  private final CeQueue ceQueue;
  private final DbClient dbClient;

  public AsyncIssueIndexingImpl(CeQueue ceQueue, DbClient dbClient) {
    this.ceQueue = ceQueue;
    this.dbClient = dbClient;
  }

  @Override
  public void triggerOnIndexCreation() {

    try (DbSession dbSession = dbClient.openSession(false)) {

      // remove already existing indexation task, if any
      removeExistingIndexationTasks(dbSession);

      dbClient.branchDao().updateAllNeedIssueSync(dbSession);
      List<BranchDto> branchInNeedOfIssueSync = dbClient.branchDao().selectBranchNeedingIssueSync(dbSession);
      LOG.info("{} branch found in need of issue sync.", branchInNeedOfIssueSync.size());

      if (branchInNeedOfIssueSync.isEmpty()) {
        return;
      }

      List<String> projectUuids = branchInNeedOfIssueSync.stream().map(BranchDto::getProjectUuid).distinct().collect(Collectors.toList());
      LOG.info("{} projects found in need of issue sync.", projectUuids.size());

      sortProjectUuids(dbSession, projectUuids);

      Map<String, List<BranchDto>> branchesByProject = branchInNeedOfIssueSync.stream()
        .collect(Collectors.groupingBy(BranchDto::getProjectUuid));

      List<CeTaskSubmit> tasks = new ArrayList<>();
      for (String projectUuid : projectUuids) {
        List<BranchDto> branches = branchesByProject.get(projectUuid);
        for (BranchDto branch : branches) {
          tasks.add(buildTaskSubmit(branch));
        }
      }

      ceQueue.massSubmit(tasks);
      dbSession.commit();
    }
  }

  private void sortProjectUuids(DbSession dbSession, List<String> projectUuids) {
    Map<String, SnapshotDto> snapshotByProjectUuid = dbClient.snapshotDao()
      .selectLastAnalysesByRootComponentUuids(dbSession, projectUuids).stream()
      .collect(Collectors.toMap(SnapshotDto::getComponentUuid, Function.identity()));

    projectUuids.sort(compareBySnapshot(snapshotByProjectUuid));
  }

  private static Comparator<String> compareBySnapshot(Map<String, SnapshotDto> snapshotByProjectUuid) {
    return (uuid1, uuid2) -> {
      SnapshotDto snapshot1 = snapshotByProjectUuid.get(uuid1);
      SnapshotDto snapshot2 = snapshotByProjectUuid.get(uuid2);
      if (snapshot1 == null) {
        return 1;
      }
      if (snapshot2 == null) {
        return -1;
      }
      return snapshot2.getCreatedAt().compareTo(snapshot1.getCreatedAt());
    };
  }

  private void removeExistingIndexationTasks(DbSession dbSession) {
    List<String> uuids = dbClient.ceQueueDao().selectAllInAscOrder(dbSession).stream()
      .filter(p -> p.getTaskType().equals(BRANCH_ISSUE_SYNC))
      .map(CeQueueDto::getUuid)
      .collect(Collectors.toList());
    LOG.info(String.format("%s pending indexation task found to be deleted...", uuids.size()));
    for (String uuid : uuids) {
      dbClient.ceQueueDao().deleteByUuid(dbSession, uuid);
    }
    dbSession.commit();

    Set<String> ceUuids = dbClient.ceActivityDao().selectByTaskType(dbSession, BRANCH_ISSUE_SYNC).stream()
      .map(CeActivityDto::getUuid)
      .collect(Collectors.toSet());
    LOG.info(String.format("%s completed indexation task found to be deleted...", uuids.size()));
    dbClient.ceActivityDao().deleteByUuids(dbSession, ceUuids);
    dbSession.commit();
    LOG.info("Indexation task deletion complete.");

    LOG.info("Deleting tasks characteristics...");
    Set<String> tasksUuid = Stream.concat(uuids.stream(), ceUuids.stream()).collect(Collectors.toSet());
    dbClient.ceTaskCharacteristicsDao().deleteByTaskUuids(dbSession, tasksUuid);
    dbSession.commit();
    LOG.info("Tasks characteristics deletion complete.");
  }

  private CeTaskSubmit buildTaskSubmit(BranchDto branch) {
    Map<String, String> characteristics = new HashMap<>();
    characteristics.put(branch.getBranchType() == BranchType.BRANCH ? BRANCH_KEY : PULL_REQUEST, branch.getKey());
    characteristics.put(BRANCH_TYPE_KEY, branch.getBranchType().name());

    return ceQueue.prepareSubmit()
      .setType(BRANCH_ISSUE_SYNC)
      .setComponent(new CeTaskSubmit.Component(branch.getUuid(), branch.getProjectUuid()))
      .setCharacteristics(characteristics).build();
  }
}
