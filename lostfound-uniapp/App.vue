<script>
export default {
  onLaunch() {
    console.log('App launched')
  },
}
</script>

<style>
page {
  background: #f5f7fa;
  font-size: 28rpx;
  color: #1f2937;
  font-family: -apple-system, 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

/* 全局工具类 */
.lf-card {
  background: #fff;
  border-radius: 20rpx;
  padding: 24rpx;
  margin-bottom: 20rpx;
  box-shadow: 0 2rpx 12rpx rgba(31, 41, 55, 0.05);
}
.lf-tag {
  display: inline-block;
  padding: 4rpx 18rpx;
  border-radius: 999rpx;
  font-size: 22rpx;
}
.lf-tag--waiting { background: #eaf1fe; color: #2563eb; }
.lf-tag--claimed { background: #fff3e8; color: #f97316; }
.lf-tag--finished { background: #e7f8f1; color: #10b981; }
.lf-tag--offline { background: #f3f4f6; color: #9ca3af; }
.lf-primary { color: #2563eb; }
.lf-accent { color: #f97316; }
.lf-muted { color: #9ca3af; font-size: 24rpx; }
.lf-btn {
  background: #2563eb;
  color: #fff;
  border-radius: 999rpx;
  font-size: 30rpx;
  text-align: center;
  padding: 22rpx 0;
}
.lf-btn--accent { background: #f97316; }
.lf-btn--plain {
  background: #fff;
  color: #2563eb;
  border: 2rpx solid #2563eb;
}
.lf-empty {
  text-align: center;
  color: #9ca3af;
  padding: 120rpx 0;
  font-size: 26rpx;
}
</style>
