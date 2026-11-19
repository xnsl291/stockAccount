"""k6 원자료(csv.gz)에서 요청 종류별 p50·p95·p99를 다시 계산함.

k6 요약(summary.json)은 연결을 맺지 못한 요청(상태 0)을 응답 시간 0으로 함께 넣어 p50을 낮춰 보이게 하므로,
응답을 받은 요청만으로 계산하고 연결을 맺지 못한 요청 수는 따로 셈. 여러 번 반복한 결과는 응답을 모두 합쳐 계산함

사용: python percentiles.py results/2026-11-19-B-hot-*.csv.gz
"""
import collections
import csv
import gzip
import sys

SKIP = {'login', 'warmup', 'hot_cancel_list'}  # 측정 대상이 아닌 준비 요청


def percentile(values, q):
    values = sorted(values)
    k = (len(values) - 1) * q
    lower = int(k)
    upper = min(lower + 1, len(values) - 1)
    return values[lower] + (values[upper] - values[lower]) * (k - lower)


durations = collections.defaultdict(list)
statuses = collections.defaultdict(collections.Counter)
for path in sys.argv[1:]:
    with gzip.open(path, 'rt', encoding='utf-8') as f:
        for row in csv.DictReader(f):
            name = row.get('name', '')
            if row['metric_name'] != 'http_req_duration' or name in SKIP:
                continue
            statuses[name][row['status']] += 1
            if row['status'] != '0':
                durations[name].append(float(row['metric_value']))

for name in sorted(durations):
    values = durations[name]
    print(f'{name:20} 응답 {len(values):6}  p50 {percentile(values, .5):9.1f}ms  p95 {percentile(values, .95):9.1f}ms  '
          f'p99 {percentile(values, .99):9.1f}ms  상태별 {dict(statuses[name])}')
