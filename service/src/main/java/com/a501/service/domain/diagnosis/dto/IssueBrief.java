package com.a501.service.domain.diagnosis.dto;

/**
 * 다른 도메인이 가져가는 요구사항의 표시용 최소 정보.
 *
 * <p>작업은 요구사항 하나에 붙습니다. 계약서는 작업 이름과 위치를 보여 주어야 하는데 그 둘이
 * 요구사항에 있어, 작업 쪽에서 이 값을 물어 갑니다. 식별자는 이 객체를 담는 map 의 키로 두므로
 * 여기에 다시 두지 않습니다.
 *
 * @param title 요구사항 제목. 계약서에서 바꾸지 않았으면 작업 이름으로 쓰입니다
 * @param detail 요구사항 설명. 계약서에서 바꾸지 않았으면 작업 설명으로 쓰입니다. 없으면 {@code null}
 * @param location 문제가 있는 위치. 없으면 {@code null}
 */
public record IssueBrief(String title, String detail, String location) {}
