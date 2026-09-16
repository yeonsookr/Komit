/**
 * 사업자 정보. 푸터와 약관·정책 화면이 같은 값을 씁니다.
 *
 * 화면마다 따로 적으면 한쪽만 고쳐져 어긋납니다. 실제 값이 정해지면 여기만 고칩니다.
 * 지금 값은 자리를 잡아 둔 것이고 아직 확정되지 않았습니다.
 */
export interface CompanyInfo {
  /** 상호 */
  name: string;
  /** 대표자명 */
  representative: string;
  /** 사업자등록번호 */
  registrationNumber: string;
  /** 통신판매업 신고번호. Komit 은 통신판매중개자입니다. (R-LEG-01) */
  mailOrderNumber: string;
  /** 소재지 */
  address: string;
  /** 문의 메일 */
  email: string;
}

export const COMPANY: CompanyInfo = {
  name: "주식회사 코밋",
  representative: "-",
  registrationNumber: "000-00-00000",
  mailOrderNumber: "제0000-서울-0000호",
  address: "서울",
  email: "help@komit.kr",
};
