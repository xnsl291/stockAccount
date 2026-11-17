// 전체 준비: 관리자 계정 하나를 만듦. 관리자 가입 API가 없어 회원으로 가입한 뒤 E2E용 MySQL에서 역할을 ADMIN으로 바꿈(설계 3.4.2)
// 관리자 토큰은 준비 코드가 종목 등록(D1)·시세 입력(D2)을 부를 때만 씀

import fs from 'node:fs';
import path from 'node:path';
import mysql from 'mysql2/promise';
import { E2E } from './config.mjs';
import { ADMIN_FILE, closeRedis, createMember } from './support/backend';

export default async function globalSetup() {
  const admin = await createMember({ name: '관리자' });
  const conn = await mysql.createConnection({
    host: 'localhost',
    port: E2E.mysqlPort,
    user: 'account',
    password: 'account',
    database: 'accountSystem',
  });
  try {
    await conn.execute("UPDATE member SET role = 'ADMIN' WHERE phone_number = ?", [admin.phone]);
  } finally {
    await conn.end();
  }
  fs.mkdirSync(path.dirname(ADMIN_FILE), { recursive: true });
  fs.writeFileSync(ADMIN_FILE, JSON.stringify({ phone: admin.phone, password: admin.password }));
  await closeRedis();
}
