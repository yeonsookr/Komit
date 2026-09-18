import { describe, expect, it } from 'vitest';
import {
  isAnswered,
  parseStep,
  questionsForStep,
  toggleOption,
  toQuestionValue,
} from './survey';
import type { SurveyQuestion } from '@/types/diagnosis';

function single(): SurveyQuestion {
  return {
    questionId: 51,
    content: '지금 어디까지 왔나요',
    answerType: 'SINGLE',
    options: [
      { optionId: 101, content: '내 컴퓨터에서만 돌아갑니다', isSelected: false },
      { optionId: 102, content: '배포는 했는데 아직 아무도 안 씁니다', isSelected: true },
    ],
  };
}

function multi(): SurveyQuestion {
  return {
    questionId: 52,
    content: '어떤 상태인가요?',
    answerType: 'MULTI',
    options: [
      { optionId: 111, content: '화면은 되는데 기능이 안 돼요', isSelected: false },
      { optionId: 113, content: '보안이 걱정돼요', isSelected: true },
      { optionId: 114, content: '곧 실제 사용자를 받아요', isSelected: true },
    ],
  };
}

function text(answer?: string): SurveyQuestion {
  return {
    questionId: 53,
    content: '무슨 일이 있었는지 적어주세요',
    answerType: 'TEXT',
    options: answer === undefined ? [] : [{ optionId: 121, content: answer, isSelected: true }],
  };
}

describe('toQuestionValue', () => {
  it('단수 문항의 고른 보기를 읽는다', () => {
    expect(toQuestionValue(single())).toEqual({ selectedOptionIds: [102], text: '' });
  });

  it('복수 문항은 고른 보기를 모두 읽는다', () => {
    expect(toQuestionValue(multi()).selectedOptionIds).toEqual([113, 114]);
  });

  it('주관식 답은 고른 보기의 content 에서 읽는다', () => {
    // 주관식 답을 담는 별도 필드는 없습니다.
    expect(toQuestionValue(text('결제가 안 돼요')).text).toBe('결제가 안 돼요');
  });

  it('아직 답하지 않은 주관식은 빈 글이다', () => {
    expect(toQuestionValue(text()).text).toBe('');
  });
});

describe('isAnswered', () => {
  it('객관식은 보기를 골라야 답한 것이다', () => {
    expect(isAnswered(single(), { selectedOptionIds: [], text: '' })).toBe(false);
    expect(isAnswered(single(), { selectedOptionIds: [101], text: '' })).toBe(true);
  });

  it('주관식은 공백만 적으면 답한 것이 아니다', () => {
    expect(isAnswered(text(), { selectedOptionIds: [], text: '   ' })).toBe(false);
    expect(isAnswered(text(), { selectedOptionIds: [], text: '안 돼요' })).toBe(true);
  });
});

describe('toggleOption', () => {
  it('단수 문항은 늘 하나만 남는다', () => {
    // 둘 이상 보내면 서버가 422 를 줍니다.
    const value = toggleOption(single(), { selectedOptionIds: [102], text: '' }, 101);
    expect(value.selectedOptionIds).toEqual([101]);
  });

  it('복수 문항은 더하고 뺀다', () => {
    const added = toggleOption(multi(), { selectedOptionIds: [113], text: '' }, 111);
    expect(added.selectedOptionIds).toEqual([113, 111]);

    const removed = toggleOption(multi(), added, 113);
    expect(removed.selectedOptionIds).toEqual([111]);
  });
});

describe('parseStep', () => {
  it('2~4 를 그대로 읽는다', () => {
    expect(parseStep('3')).toBe(3);
    expect(parseStep('4')).toBe(4);
  });

  it('범위를 벗어나거나 숫자가 아니면 2로 되돌린다', () => {
    expect(parseStep('1')).toBe(2);
    expect(parseStep('5')).toBe(2);
    expect(parseStep('abc')).toBe(2);
    expect(parseStep(undefined)).toBe(2);
  });
});

describe('questionsForStep', () => {
  const questions: SurveyQuestion[] = [single(), multi(), text('결제가 안 돼요'), single()];

  it('설문1은 진단을 열기 전이라 문항이 없다', () => {
    expect(questionsForStep(questions, 1)).toEqual([]);
  });

  it('설문2는 세 문항을 함께 받는다', () => {
    expect(questionsForStep(questions, 2).map((q) => q.answerType)).toEqual([
      'SINGLE',
      'MULTI',
      'TEXT',
    ]);
  });

  it('설문3은 검사 파일 단계라 문항이 없다', () => {
    expect(questionsForStep(questions, 3)).toEqual([]);
  });

  it('설문4는 고정 문항 뒤에 오는 것을 모두 받는다', () => {
    expect(questionsForStep(questions, 4)).toHaveLength(1);
    expect(questionsForStep([...questions, single(), single()], 4)).toHaveLength(3);
  });

  it('검사 전이라 설문4 문항이 없으면 빈 배열이다', () => {
    expect(questionsForStep(questions.slice(0, 3), 4)).toEqual([]);
  });
});
