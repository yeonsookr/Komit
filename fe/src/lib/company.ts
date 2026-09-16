/**
 * 사업자 정보. 전자상거래법이 공개하도록 정한 항목입니다.
 *
 * 지금은 푸터만 쓰지만 화면마다 따로 적으면 한쪽만 고쳐져 어긋나므로 한 곳에 둡니다.
 * 실제 값이 정해지면 여기만 고칩니다. 지금 값은 자리를 잡아 둔 것이고
 * 아직 확정되지 않았습니다.
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
