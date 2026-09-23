package com.a501.service.domain.recommendation.service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 개발자 추천 점수. 고른 요구사항이 걸린 프로젝트의 기술과 개발자 프로필을 정해진 규칙으로 견준다. (R-MATCH-05)
 *
 * <p>규칙 기반이다. 같은 입력이면 언제 계산해도 같은 값이 나오고, 왜 그 순위인지 항목별 값으로 설명할 수 있어야 한다.
 *
 * <p><b>가중치는 아직 팀 합의가 없다.</b> {@code RULES.md} 「미확정」에 기술·가격·평점·경력의 가중치와 동점 정렬 기준을 정해야 한다고 적혀 있다. 여기 값은 그
 * 합의 전까지 쓰는 {@link #VERSION} 판이다. 계수가 바뀌어도 판 번호만 올리면 이전 회차와 구분된다.
 *
 * <p>가격은 넣지 않는다. 의뢰인 예산을 담는 자리가 없어, 가격을 쓰면 싼 개발자가 위로 오는 정책이 되어 버린다.
 */
public final class MatchScore {
    /** 알고리즘 판. 회차마다 기록해 판 사이의 성과를 견준다. */
    public static final String VERSION = "match-v0";

    private static final double TECH_WEIGHT = 0.6;
    private static final double RATING_WEIGHT = 0.3;
    private static final double CAREER_WEIGHT = 0.1;

    /** 견줄 것이 없을 때 쓰는 값. 0 을 주면 신규 개발자가 영영 위로 올라오지 못한다. */
    private static final double NEUTRAL = 0.5;
    /** 경력을 1 로 보는 연차. 이보다 길어도 더 오르지 않는다. */
    private static final double CAREER_FULL_YEARS = 10.0;
    /** 증빙된 경력에 얹는 값. */
    private static final double VERIFIED_CAREER_BONUS = 0.1;
    /** 개발력과 설명력의 무게. 의뢰인이 결과물을 먼저 본다고 보고 개발력을 더 준다. */
    private static final double DEV_RATING_WEIGHT = 0.7;
    private static final double MAX_RATING = 5.0;

    private MatchScore() {
    }

    /**
     * @param projectTechCodeIds 검사에서 확인한 프로젝트 기술. 비어 있으면 기술로는 가리지 않는다
     * @param candidate          점수를 매길 개발자
     * @return 총점과 항목별 값
     */
    public static Scored of(Set<Integer> projectTechCodeIds, Candidate candidate) {
        double tech = techMatch(projectTechCodeIds, candidate.techCodeIds());
        double rating = rating(candidate);
        double career = career(candidate);

        double total = TECH_WEIGHT * tech + RATING_WEIGHT * rating + CAREER_WEIGHT * career;

        Map<String, Double> breakdown = new LinkedHashMap<>();
        breakdown.put("tech", round(tech));
        breakdown.put("rating", round(rating));
        breakdown.put("career", round(career));
        return new Scored(round(total), breakdown);
    }

    /** 프로젝트가 쓰는 기술 가운데 개발자가 다룰 수 있는 비율. */
    private static double techMatch(Set<Integer> projectTechCodeIds, Set<Integer> developerTechCodeIds) {
        if (projectTechCodeIds.isEmpty()) {
            return NEUTRAL;
        }
        long matched = projectTechCodeIds.stream().filter(developerTechCodeIds::contains).count();
        return (double) matched / projectTechCodeIds.size();
    }

    /** 리뷰가 없으면 가리지 않는다. 아직 받은 적이 없는 것과 나쁜 것은 다르다. */
    private static double rating(Candidate candidate) {
        Double dev = candidate.devRating();
        Double explanatory = candidate.explanatoryRating();
        if (dev == null && explanatory == null) {
            return NEUTRAL;
        }
        double devPart = dev == null ? NEUTRAL : dev / MAX_RATING;
        double explanatoryPart = explanatory == null ? NEUTRAL : explanatory / MAX_RATING;
        return DEV_RATING_WEIGHT * devPart + (1 - DEV_RATING_WEIGHT) * explanatoryPart;
    }

    private static double career(Candidate candidate) {
        double years = candidate.yearsOfExperience() == null ? 0 : candidate.yearsOfExperience();
        double base = Math.min(years / CAREER_FULL_YEARS, 1);
        double bonus = candidate.hasVerifiedCareer() ? VERIFIED_CAREER_BONUS : 0;
        return Math.min(base + bonus, 1);
    }

    /** 소수점 넷째 자리까지 둔다. 저장 컬럼이 그 자리까지 받는다. */
    private static double round(double value) {
        return Math.round(value * 10_000) / 10_000.0;
    }

    /** 점수를 매길 개발자. 다른 도메인의 엔티티를 들이지 않으려고 필요한 값만 받는다. */
    public record Candidate(
            Integer developerId,
            Set<Integer> techCodeIds,
            Double devRating,
            Double explanatoryRating,
            int reviewCount,
            Double yearsOfExperience,
            boolean hasVerifiedCareer) {
        public Candidate {
            techCodeIds = techCodeIds == null ? Set.of() : Set.copyOf(techCodeIds);
        }

        public static Candidate of(
                Integer developerId,
                Collection<Integer> techCodeIds,
                Double devRating,
                Double explanatoryRating,
                int reviewCount,
                Double yearsOfExperience,
                boolean hasVerifiedCareer) {
            return new Candidate(
                    developerId,
                    techCodeIds == null ? Set.of() : Set.copyOf(techCodeIds),
                    devRating,
                    explanatoryRating,
                    reviewCount,
                    yearsOfExperience,
                    hasVerifiedCareer);
        }
    }

    /** 총점과 항목별 값. 항목별 값은 회차에 함께 저장해 순위를 설명하는 데 쓴다. */
    public record Scored(double total, Map<String, Double> breakdown) {}
}
