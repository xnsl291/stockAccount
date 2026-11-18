-- 평가손익은 조회 응답이 현재가로 계산하고 저장하지 않으므로 칸을 지움.
-- 이미 적용된 V1을 고치면 V1을 적용해 둔 다른 DB에서 Flyway 체크섬 검사가 깨지므로 새 파일로 지움
ALTER TABLE `stock_balance` DROP COLUMN `profitnloss`;
