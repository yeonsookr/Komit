/**
 * 코밋 서비스 API의 <b>기준(default) API</b> 구현. 목 명세 {@code /service/docs}의 {@code default} 태그에 대응합니다.
 *
 * <p>이 도메인은 화면 기능이 아니라 <b>다른 도메인이 그대로 따라 쓰는 본보기</b>입니다. 계층 책임, 계약 응답 형식,
 * 페이지네이션, QueryDSL 목록 조회, 에러 코드, 문서화, 테스트가 이 패키지의 모양대로 갑니다.
 *
 * <pre>
 * domain/defaults/
 * ├── controller/   DefaultController, DefaultSortEditor
 * ├── service/      DefaultService
 * ├── repository/   DefaultRepository (+ Custom, Impl = QueryDSL)
 * ├── entity/       Default
 * ├── dto/request/  DefaultSort
 * ├── dto/response/ DefaultResponse, DefaultListResponse
 * └── DefaultErrorCode
 * </pre>
 *
 * <p>패키지 이름이 도메인 이름 규칙(단수형)과 달리 {@code defaults}인 이유는 {@code default}가 자바 예약어라
 * 패키지 이름으로 쓸 수 없기 때문입니다. API 경로와 태그는 계약대로 {@code default}입니다.
 *
 * <p><b>목과 다른 점(재현하지 않는 것)</b>
 * <ul>
 *   <li>목의 예약값({@code id}=2·3·4, {@code page}=99)은 화면에서 실패 응답을 보기 위한 목 전용 장치입니다.
 *       실서버는 값으로 분기하지 않고 실제 상태(행 존재·삭제 여부)로 응답합니다.</li>
 *   <li>403 {@code FORBIDDEN}은 이 도메인에서 나올 조건이 없습니다. 자세한 이유는
 *       {@link com.a501.service.domain.defaults.service.DefaultService}를 보세요.</li>
 *   <li>전체 건수 137건은 목의 고정값입니다. 실서버는 {@code defaults} 테이블의 살아 있는 행 수를 셉니다.</li>
 * </ul>
 */
package com.a501.service.domain.defaults;
