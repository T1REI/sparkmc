use std::cmp::Ordering;

pub const MIN_HEAP_MB: i64 = 512;

pub fn heap_mb(total_mb: u32) -> i64 {
    (11 * total_mb as i64) / 12 - 1200
}

pub fn cmp_version(a: &str, b: &str) -> Ordering {
    numeric_parts(a).cmp(&numeric_parts(b))
}

fn numeric_parts(v: &str) -> Vec<u64> {
    v.split(|c: char| !c.is_ascii_digit())
        .filter(|s| !s.is_empty())
        .filter_map(|s| s.parse::<u64>().ok())
        .collect()
}
