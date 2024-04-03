# 프로젝트 기능

1. 회원가입
   - 전화번호 id, 인증 필요
   - 이름
   - 로그인 비밀번호
     
2. 로그인 / 로그아웃
   - 전화번호(id), 로그인 비밀번호 
   - 회원가입과 로그인을 제외한 모든 기능 로그인 필수

3. 계좌 관리 (개설 / 해지 / 수정 / 조회 )
   - 회원가입 시, 자동으로 1개의 계좌가 개설
   - 모든 회원은 최소 1개의 계좌 보유 필수 
   - 계좌 추가 개설 및 해지 가능
   - 계좌 조회 시, 본인 명의의 계좌들과 잔액 조회 가능
     
4. 계좌 거래 (입금 / 출금 / 송금 )
   - 계좌번호 입력 시, 계좌 소유주 조회 가능 
   - 자신의 잔액 내에서 출금 가능
   - 계좌 비밀번호 인증 필수 
     
5. 계좌 거래 내역 조회
   - 본인 계좌 별 거래 내역 조회 가능
   - 거래 일시, 거래 대상, 금액, 메모 포함 
   - 거래 내역은 최근 순으로 정렬됨 
   - 특정 기간만 필터링 하여 조회 가능
     
6. 주식 거래 (매수 / 매도)
   - 주식의 현재 시세는 `한국투자증권 오픈 API`를 통해 받아온다 **(*변경예정)**
   - 각 계좌마다 독립적으로 주식을 매매 가능
   - 주식 매매 시 금액, 종목 코드, 수량 정보 필요 & 비밀번호 인증 필수 

   - 매수 
      - 계좌 잔액 내에서 주식 매수 가능
      - 기존에 보유하고 있는 주식을 추가 구매 시, 
<br>`((보유주식 단가 * 수량) + (추가 구매한 주식 단가*수량)) / 해당 종목 총 수량` 으로 계산하여 평균단가 계산.
   - 매도
      - 주식을 매도하는 즉시 잔액 출금이 가능  **(*변경예정)**
      - 매도시 본인 보유 수량 초과 불가 
   - 계좌 비밀번호 인증 필수 
    
7. 주식 잔고 이체 (주식 이체)
   - 한 계좌에서 다른 계좌로 주식을 이체 가능
   - 본인의 보유 수량을 초과하여 송금 불가능 
   - 본인 멍의의 계좌로 보낼 경우, 수수료 X 
   - 타인 명의로 보낼 경우, `주식 가격 * 송금 수량 * 0.01` 이 수수료로 차감
   - 계좌 비밀번호 인증 필수 

8. 조회 ( 현재 주식 잔고 / 주식 매매 내역 )
   - 주식 잔고 조회 
      - 현재 보유하고 있는 주식 조회시 종목코드, 평균단가, 수량, 평가손익 정보 포함
   - 매매 내역 조회 
      - 매매 내역은 최근순으로 정렬됨 
      - 매매내역 조회시, 종목코드, 매매일시, 매매가, 수량, 손익정보 포함 
   - 특정 기간만 필터링하여 조회 가능
     
9. 검색 (계좌 / 종목)
   - 계좌 검색 
      - 계좌번호로 계좌 검색
      - 본인 계좌 -> 계좌번호, 잔액 조회
      - 타인 계좌 -> 계좌번호, 계좌 소유주명 조회 
   - 종목 검색 
      - 종목코드 / 종목명 입력 -> 현재 가격 조회
      
10. 공통
   - 비밀번호 오류 최대 5회, 이후 비밀번호 재설정 (핸드폰 인증 필요)
   - 비밀번호 인증에 성공 혹은 마지막 시도로부터 2시간 후 -> 오류 횟수 0회로 초기화


## 개선사항
- 주식 정보 저장방식 변경
   - 기존: 1일 1회 주식 종목 전체 정보 크롤링 (종목코드, 종목명, 종가 DB 저장- 실시간 반영 x ) 
   - 개선 방안 1. 1일 1회 종목정보만 크롤링 (종목 코드, 종목명) + 종목 조회 요청시마다 크롤링
      - 문제점 : 여러 종목을 조회할 경우, 서비스 시간 증가
   - 개선 방안 2. 한국투자증권 api 사용
      - 문제점 : 1일 1회 인증 필요
- 속도 관련 내용 후첨 예정(*)    

# ERD
![img.png](doc/img/img.png)

# API 명세
https://wiry-mimosa-01b.notion.site/API-5b1ccf98ee834a2689915d3833ca3e63?pvs=4

# 사용 기술
- Java 11
- Spring
- Spring Security
- JWT 
- Mysql
- Redis
- CoolSMS

# 추가/변경 예정
- 출금/ 송금은 매매일로부터 2일 뒤부터 가능하다. -> D-1예수금, D-2예수금 조회가능
- 관심주를 저장해놓고, 편하게 시세를 확인할 수 있다.
- [시세를 크롤링으로 받아올 경우] 주식 가격 실시간 반영 / 평가손익 실시간 반영 / 종목 순위 (거래량 상위 종목)
- 알림기능 (매매시, 혹은 주식이 특정 금액에 도달시 )
- 보유종목 배당금 조회
- 종목 코드가 아닌 종목명으로 검색
- 검색 시, 유사 종목명 보여주기 (엘라스틱 서치 - 자동완성)
- 인덱스 설계
- 주식정보 크롤링 
