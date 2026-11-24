// E2E 환경의 포트와 접속 정보. 준비 스크립트·백엔드 실행 스크립트·테스트가 같이 씀

export const E2E = {
  mysqlPort: 3317,
  redisPort: 6389,
  // 기본 앱(액세스 토큰 30분)과 E-08·E-12용 짧은 만료 앱. 두 앱은 같은 DB·Redis를 씀
  backendPort: 8081,
  shortBackendPort: 8082,
  shortAccessSeconds: 5,
  webPort: 5174,
  shortWebPort: 5175,
};
