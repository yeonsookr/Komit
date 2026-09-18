package com.a501.service.domain.code.entity;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SystemEnumId implements Serializable {
    @Column(name = "code_key", nullable = false, length = 50)
    private String codeKey;

    @Column(name = "group_code", nullable = false, length = 50)
    private String groupCode;
}
