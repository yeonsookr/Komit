package com.a501.service.domain.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.a501.service.domain.recommendation.service.MatchScore.Candidate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 추천 점수 규칙. 같은 입력이면 언제 계산해도 같은 값이어야 하고, 왜 그 순위인지 항목별 값으로 설명할 수 있어야 한다. (R-MATCH-05)
 */
class MatchScoreTest {
    /** 검사가 확인한 프로젝트 기술. 21 Next.js · 23 TypeScript · 24 React */
    private static final Set<Integer> PROJECT_TECH = Set.of(21, 23, 24);

    @Test
    @DisplayName("프로젝트 기술을 모두 다루면 기술 항이 1이다")
    void fullTechMatch() {
        MatchScore.Scored scored = MatchScore.of(PROJECT_TECH, candidate(Set.of(21, 23, 24), 5.0, 5.0, 10, 10.0, true));

        assertThat(scored.breakdown()).containsEntry("tech", 1.0);
        assertThat(scored.total()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("기술 항은 프로젝트 기술 가운데 다룰 수 있는 비율이다")
    void partialTechMatch() {
        assertThat(score(Set.of(21, 23)).breakdown()).containsEntry("tech", 0.6667);
        assertThat(score(Set.of(21)).breakdown()).containsEntry("tech", 0.3333);
        assertThat(score(Set.of()).breakdown()).containsEntry("tech", 0.0);
    }

    @Test
    @DisplayName("프로젝트 기술이 없으면 기술로 가리지 않는다")
    void noProjectTechIsNeutral() {
        assertThat(MatchScore.of(Set.of(), candidate(Set.of(21), 4.0, 4.0, 5, 3.0, false)).breakdown())
                .containsEntry("tech", 0.5);
    }

    @Test
    @DisplayName("프로젝트가 쓰지 않는 기술은 점수를 올리지 않는다")
    void extraTechDoesNotHelp() {
        assertThat(score(Set.of(21, 23, 24)).total())
                .isEqualTo(score(Set.of(21, 22, 23, 24)).total());
    }

    @Test
    @DisplayName("리뷰가 없으면 평점으로 가리지 않는다. 받은 적 없는 것과 나쁜 것은 다르다")
    void noReviewIsNeutral() {
        assertThat(MatchScore.of(PROJECT_TECH, candidate(Set.of(21), null, null, 0, 1.0, false)).breakdown())
                .containsEntry("rating", 0.5);
    }

    @Test
    @DisplayName("평점은 개발력에 더 무게를 둔다")
    void devRatingWeighsMore() {
        double devHigh = MatchScore.of(PROJECT_TECH, candidate(Set.of(), 5.0, 1.0, 5, 0.0, false))
                .breakdown().get("rating");
        double explanatoryHigh = MatchScore.of(PROJECT_TECH, candidate(Set.of(), 1.0, 5.0, 5, 0.0, false))
                .breakdown().get("rating");

        assertThat(devHigh).isGreaterThan(explanatoryHigh);
    }

    @Test
    @DisplayName("경력은 10년에서 1이 되고 증빙이 있으면 더 얹되 1을 넘지 않는다")
    void careerCapsAtOne() {
        assertThat(career(5.0, false)).isEqualTo(0.5);
        assertThat(career(5.0, true)).isEqualTo(0.6);
        assertThat(career(30.0, true)).isEqualTo(1.0);
        assertThat(career(null, false)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("기술을 더 많이 맞추는 쪽이 평점만 높은 쪽보다 위다")
    void techOutweighsRating() {
        MatchScore.Scored allTech = MatchScore.of(PROJECT_TECH, candidate(Set.of(21, 23, 24), 4.0, 4.0, 3, 2.0, false));
        MatchScore.Scored perfectRating = MatchScore.of(PROJECT_TECH, candidate(Set.of(22), 5.0, 5.0, 30, 8.0, true));

        assertThat(allTech.total()).isGreaterThan(perfectRating.total());
    }

    @Test
    @DisplayName("아무것도 없는 신규 개발자도 0점은 아니다")
    void newcomerIsNotZero() {
        assertThat(MatchScore.of(PROJECT_TECH, candidate(Set.of(), null, null, 0, null, false)).total())
                .isGreaterThan(0.0);
    }

    @Test
    @DisplayName("확인용 개발자 다섯 명의 순위가 의도대로 나온다")
    void seededDevelopersRankAsIntended() {
        record Row(int id, MatchScore.Scored scored) {}

        List<Row> ranked = List.of(
                        new Row(9101, MatchScore.of(PROJECT_TECH, candidate(Set.of(21, 23, 24), 4.8, 4.6, 12, 5.0, true))),
                        new Row(9102, MatchScore.of(PROJECT_TECH, candidate(Set.of(21, 23), 4.2, 4.0, 3, 2.0, false))),
                        new Row(9103, MatchScore.of(PROJECT_TECH, candidate(Set.of(22), 5.0, 5.0, 1, 8.0, false))),
                        new Row(9104, MatchScore.of(PROJECT_TECH, candidate(Set.of(), null, null, 0, 1.0, false))),
                        new Row(9105, MatchScore.of(PROJECT_TECH, candidate(Set.of(21, 24), null, null, 0, 10.0, true))))
                .stream()
                .sorted(Comparator.comparingDouble((Row row) -> row.scored().total()).reversed())
                .toList();

        assertThat(ranked.stream().map(Row::id))
                .as("""
                        9101 기술을 다 맞추고 평점도 높아 1등.
                        9102 와 9105 는 기술이 같지만, 받은 평점이 있는 9102 가 경력만 긴 9105 보다 위다.
                        9103 은 평점이 만점이어도 기술이 하나뿐이라 아래. 9104 는 견줄 것이 없어 끝이다.""")
                .containsExactly(9101, 9102, 9105, 9103, 9104);
    }

    private static MatchScore.Scored score(Set<Integer> developerTech) {
        return MatchScore.of(PROJECT_TECH, candidate(developerTech, 4.0, 4.0, 5, 3.0, false));
    }

    private static double career(Double years, boolean verified) {
        return MatchScore.of(PROJECT_TECH, candidate(Set.of(), 4.0, 4.0, 5, years, verified))
                .breakdown()
                .get("career");
    }

    private static Candidate candidate(
            Set<Integer> techCodeIds,
            Double devRating,
            Double explanatoryRating,
            int reviewCount,
            Double years,
            boolean verified) {
        return Candidate.of(1, techCodeIds, devRating, explanatoryRating, reviewCount, years, verified);
    }
}
