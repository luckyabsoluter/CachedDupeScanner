# CachedDupeScanner DB 조회 구조 문서 (Room/SQLite)

이 문서는 앱에서 **어떤 시나리오에서 DB를 조회/갱신하는지**, 그때 **어떤 쿼리(DAO/SQL)가 실행되는지**, 그리고 **현재 인덱스 설계가 어떤 쿼리에 유효한지**를 한 곳에 정리한 문서입니다.

대상 DB는 Room 기반 SQLite 파일 `scan-cache.db`이며, 스키마는 `CacheDatabase`(v8) 기준입니다.

## 1) 스키마 개요

### 1.1 `cached_files`

파일 캐시(경로/크기/mtime/해시)를 저장합니다.

- PK: `normalizedPath`
- Columns: `normalizedPath`, `path`, `sizeBytes`, `lastModifiedMillis`, `hashHex`
- 인덱스: **PK 인덱스(= normalizedPath)**

> 참고: `cached_files`는 프로젝트 초기 버전부터 존재하며, 마이그레이션 파일에는 생성 SQL이 남아있지 않습니다(`MIGRATION_1_3` 주석 참고).

### 1.2 `scan_reports`

스캔 실행 결과 요약(소요시간/카운트/타깃 목록)을 저장합니다.

- PK: `id`
- Columns: `startedAtMillis`, `finishedAtMillis`, `targetsText`, `mode`, `cancelled`, …
- 인덱스: **PK 인덱스(= id)**

### 1.3 `trash_entries`

휴지통 이동(파일 삭제 대신 파일 이동) 기능을 위한 테이블입니다.

- PK: `id`
- Columns: `originalPath`, `trashedPath`, `sizeBytes`, `lastModifiedMillis`, `deletedAtMillis`, `volumeRoot`
- 인덱스:
  - `index_trash_entries_deletedAtMillis` (정렬/목록 조회)
  - `index_trash_entries_originalPath` (원본경로 기준 조회/중복 대응)

## 2) DAO와 실제 SQL

Room DAO에 명시된 `@Query`는 그대로 SQL이 됩니다. `@Insert(onConflict = REPLACE)`는 SQLite의 `INSERT OR REPLACE`로 동작합니다.

### 2.1 `FileCacheDao` (cached_files)

- `getByNormalizedPath(normalizedPath)`
  - SQL: `SELECT * FROM cached_files WHERE normalizedPath = ? LIMIT 1`
  - 인덱스: PK(`normalizedPath`)로 즉시 탐색

- `getAll()`
  - SQL: `SELECT * FROM cached_files`
  - 인덱스: 풀스캔(대량 데이터일수록 비용 큼)

- `countAll()`
  - SQL: `SELECT COUNT(*) FROM cached_files`

- `getPageAfter(afterPath, limit)`
  - SQL: `SELECT * FROM cached_files WHERE normalizedPath > ? ORDER BY normalizedPath LIMIT ?`
  - 인덱스: PK(`normalizedPath`) 정렬/범위 스캔에 유리

- `upsert(entity)` / `upsertAll(entities)`
  - SQL: `INSERT OR REPLACE INTO cached_files (...) VALUES (...)`

- `deleteByNormalizedPath(normalizedPath)`
  - SQL: `DELETE FROM cached_files WHERE normalizedPath = ?`
  - 인덱스: PK 기반 삭제

- `clear()`
  - SQL: `DELETE FROM cached_files`

- `countBySizes(sizes)`
  - SQL: `SELECT sizeBytes, COUNT(*) FROM cached_files WHERE sizeBytes IN (...) GROUP BY sizeBytes`
  - 인덱스: 현재는 `sizeBytes` 인덱스가 없어 **큰 테이블에서 비용 증가 가능**

- `findSizesByPaths(paths)`
  - SQL: `SELECT normalizedPath, sizeBytes FROM cached_files WHERE normalizedPath IN (...)`
  - 인덱스: PK(`normalizedPath`)로 유리

### 2.2 `ScanReportDao` (scan_reports)

- `getAll()`
  - SQL: `SELECT * FROM scan_reports ORDER BY startedAtMillis DESC`
  - 인덱스: 현재 `startedAtMillis` 인덱스가 없어 정렬 비용 발생 가능

- `getById(reportId)`
  - SQL: `SELECT * FROM scan_reports WHERE id = ? LIMIT 1`
  - 인덱스: PK(`id`)로 즉시 탐색

- `upsert(report)`
  - SQL: `INSERT OR REPLACE INTO scan_reports (...) VALUES (...)`

- `clearAll()`
  - SQL: `DELETE FROM scan_reports`

### 2.3 `TrashDao` (trash_entries)

- `getAll()`
  - SQL: `SELECT * FROM trash_entries ORDER BY deletedAtMillis DESC`
  - 인덱스: `deletedAtMillis` 인덱스로 정렬 비용 완화

- `getById(id)`
  - SQL: `SELECT * FROM trash_entries WHERE id = ? LIMIT 1`
  - 인덱스: PK(`id`)로 즉시 탐색

- `upsert(entry)`
  - SQL: `INSERT OR REPLACE INTO trash_entries (...) VALUES (...)`

- `deleteById(id)`
  - SQL: `DELETE FROM trash_entries WHERE id = ?`

- `clear()`
  - SQL: `DELETE FROM trash_entries`

## 3) 시나리오별 “언제 어떤 쿼리”가 실행되는가

이 섹션은 사용자의 화면/동작 기준으로 DB 접근을 나눕니다.

### 3.1 앱 시작 시: 이전 캐시 로드

- 트리거: 앱 시작 후 `Idle` 상태에서 이전 기록을 불러옴
- 호출 흐름:
  - `MainActivity` → `ScanHistoryRepository.loadMergedHistory()`
- 실행 쿼리:
  - `FileCacheDao.getAll()`
- 특징/주의:
  - 전체 로딩(`SELECT *`)이므로 캐시가 아주 커질 경우(수십만~) 메모리/시간 부담이 커짐

### 3.2 스캔 수행(증분 스캔): 캐시 조회/후보선정/저장

- 트리거: 스캔 실행
- 호출 흐름:
  - `IncrementalScanner.scan()`
  - 캐시 관련은 `CacheStore`를 통해 `FileCacheDao` 호출

**(A) 사이즈 충돌 후보 선정을 위한 통계 조회**

- 실행 쿼리:
  - `FileCacheDao.countBySizes(sizes)`
- 구현 상세:
  - SQLite 바인딩 변수 제한(900)을 피하기 위해 `CacheStore.countBySizes()`가 `sizes`를 chunk로 쪼갬

**(B) 개별 파일 캐시 히트/미스/스테일 판정**

- 실행 쿼리(파일 수만큼 반복 가능):
  - `FileCacheDao.getByNormalizedPath(normalizedPath)`
- 인덱스:
  - PK 탐색이라 단건은 빠르지만, 호출 횟수가 많으면 누적 비용이 커질 수 있음

**(C) 스캔 결과 캐시 저장**

- 실행 쿼리:
  - `FileCacheDao.upsertAll(entities)`
- 구현 상세:
  - 500개 단위로 chunk 저장

### 3.3 DB 관리 화면(maintenance)

- 트리거: DB management 화면에서 maintenance 실행
- 호출 흐름:
  - `DbManagementScreen` → `ScanHistoryRepository.runMaintenance()`

- 실행 쿼리:
  - `countAll()` (총량)
  - 반복: `getPageAfter(lastPath, batchSize=200)`
  - 조건부: `deleteByNormalizedPath(path)`
  - 조건부: `upsert(updatedEntity)`

- 인덱스:
  - `getPageAfter`는 `normalizedPath` PK를 이용한 **range scan + ORDER BY**로 구현되어 페이징에 적합

### 3.4 파일 목록/결과 화면에서 “삭제(=휴지통 이동)”

- 트리거: 결과/파일 화면에서 삭제 요청
- 호출 흐름:
  - UI → `TrashController.moveToTrash(normalizedPath)`

- 파일시스템 동작:
  - 대상 파일을 해당 볼륨 루트의 `.CachedDupeScanner/trashbin/`로 이동

- DB 트랜잭션(원자적 처리):
  - `database.runInTransaction {`
    - `FileCacheDao.deleteByNormalizedPath(normalizedPath)`
    - `TrashDao.upsert(entry)`
  - `}`

- 의미:
  - “파일 이동 성공 + DB 업데이트 성공”을 한 덩어리로 다루며, DB 실패 시 파일 이동을 되돌리려는 best-effort 롤백이 있음

### 3.5 쓰레기통 화면 조회

- 트리거: Trash 화면 진입/새로고침
- 호출 흐름:
  - `TrashScreen` → `TrashRepository.listAll()`
- 실행 쿼리:
  - `TrashDao.getAll()`
- 인덱스:
  - `deletedAtMillis` 인덱스로 최신순 정렬에 유리

### 3.6 쓰레기통 복원/영구삭제/비우기

- 복원:
  - 파일 이동(trashedPath → originalPath)
  - 성공 시: `TrashDao.deleteById(id)`

- 영구삭제:
  - 파일 삭제(trashedPath)
  - 성공 시: `TrashDao.deleteById(id)`

- 비우기:
  - `TrashDao.getAll()`로 목록 로드 후 각 항목을 영구삭제 + `deleteById`

### 3.7 리포트 화면

- 목록:
  - `ScanReportDao.getAll()` (startedAtMillis 내림차순)
- 상세:
  - `ScanReportDao.getById(id)`

## 4) 인덱스 적합성 평가 및 권장 설계

현재 인덱스는 “기본 PK + trash 정렬용 인덱스” 중심입니다. 아래는 쿼리 패턴 기준의 권장 사항입니다.

### 4.1 `cached_files(sizeBytes)` 인덱스 권장

- 근거 쿼리: `countBySizes()`
- 효과: `WHERE sizeBytes IN (...) GROUP BY sizeBytes`가 큰 테이블에서 풀스캔/임시정렬 비용이 커질 수 있어, `sizeBytes` 인덱스가 있으면 후보군 필터링 비용을 줄일 가능성이 큼

권장 DDL(예시):

```sql
CREATE INDEX IF NOT EXISTS index_cached_files_sizeBytes ON cached_files(sizeBytes);
```

> 현재 코드에는 이 인덱스를 추가하는 마이그레이션이 없으며, “문서화” 범위에서 제안만 합니다.

### 4.2 `scan_reports(startedAtMillis)` 인덱스 권장

- 근거 쿼리: `getAll() ORDER BY startedAtMillis DESC`
- 효과: 리포트가 누적될수록 정렬 비용 증가

권장 DDL(예시):

```sql
CREATE INDEX IF NOT EXISTS index_scan_reports_startedAtMillis ON scan_reports(startedAtMillis);
```

### 4.3 이미 적절한 인덱스

- `cached_files.normalizedPath` PK
  - `getByNormalizedPath`, `deleteByNormalizedPath`, `getPageAfter`에 적절
- `trash_entries.deletedAtMillis` 인덱스
  - trash 목록 최신순 정렬에 적절

## 5) 성능/안정성 관찰 포인트

- `cached_files.getAll()`은 데이터가 커지면 앱 시작/병합에 부담이 됩니다. 장기적으로는 “부분 로딩 + 필요 시 조회” 구조 또는 “요약 테이블/인덱스 기반 후보 조회”가 필요할 수 있습니다.
- 증분 스캔에서 `getByNormalizedPath()`가 파일 수만큼 반복될 수 있습니다. PK 탐색이 빠르더라도, 호출 수가 커지면 총 시간이 커집니다(배치 조회/프리페치로 최적화 여지).
- trash 비우기는 현재 `getAll()` 후 반복 삭제 방식입니다. 항목 수가 커지면 UI/IO 시간 증가 가능성이 있으므로, 추후 배치 처리/진행 표시 등을 고려할 수 있습니다.

---

## 부록: 관련 코드 위치(참고용)

- DB 정의: `CacheDatabase`, `CacheMigrations`
- 캐시 DAO: `FileCacheDao`
- 리포트 DAO: `ScanReportDao`
- 휴지통 DAO: `TrashDao`
- 증분 스캐너: `IncrementalScanner`, `CacheStore`
- 휴지통 제어: `TrashController`
