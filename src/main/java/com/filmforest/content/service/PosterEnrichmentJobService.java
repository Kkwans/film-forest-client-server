package com.filmforest.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.filmforest.common.exception.BusinessException;
import com.filmforest.content.dto.PageResult;
import com.filmforest.content.dto.PosterEnrichmentJobView;
import com.filmforest.content.dto.PosterSettingView;
import com.filmforest.content.entity.PosterEnrichmentJob;
import com.filmforest.content.mapper.PosterEnrichmentJobMapper;
import com.filmforest.content.model.ContentType;
import com.filmforest.content.poster.PosterBatchContentSource;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Service
public class PosterEnrichmentJobService {

    private static final Set<String> ACTIVE_STATUSES = Set.of("queued", "running", "cancel_requested");

    private final PosterEnrichmentJobMapper mapper;
    private final UserPosterSettingService settingService;
    private final PosterBatchContentSource contentSource;
    private final PosterEnrichmentJobWorker worker;

    public PosterEnrichmentJobService(PosterEnrichmentJobMapper mapper,
                                      UserPosterSettingService settingService,
                                      PosterBatchContentSource contentSource,
                                      PosterEnrichmentJobWorker worker) {
        this.mapper = mapper;
        this.settingService = settingService;
        this.contentSource = contentSource;
        this.worker = worker;
    }

    public PosterEnrichmentJobView start(long userId, String rawContentType) {
        PosterSettingView setting = settingService.get(userId);
        if (!"tmdb".equals(setting.posterSource())) {
            throw new BusinessException("请先选择 TMDB 智能海报模式");
        }
        if (!setting.configured()) throw new BusinessException("请先配置 TMDB 凭据");
        ContentType selected = rawContentType == null || rawContentType.isBlank()
                ? null : ContentType.parse(rawContentType);
        PosterEnrichmentJob active = active(userId);
        if (active != null) return view(active);

        long total = selected == null
                ? Arrays.stream(ContentType.values()).mapToLong(contentSource::count).sum()
                : contentSource.count(selected);
        PosterEnrichmentJob job = new PosterEnrichmentJob();
        job.setUserId(userId);
        job.setStatus("queued");
        job.setCancelRequested(0);
        job.setContentType(selected == null ? null : selected.code());
        job.setTotalCount((int) Math.min(Integer.MAX_VALUE, total));
        job.setProcessedCount(0);
        job.setMatchedCount(0);
        job.setPendingCount(0);
        job.setFailedCount(0);
        job.setQueuedAt(now());
        try {
            mapper.insert(job);
        } catch (DuplicateKeyException concurrentStart) {
            PosterEnrichmentJob concurrent = active(userId);
            if (concurrent != null) return view(concurrent);
            throw new BusinessException(409, "已有海报补全任务正在运行");
        }
        worker.run(job.getId());
        return view(job);
    }

    public PosterEnrichmentJobView latest(long userId) {
        PosterEnrichmentJob job = mapper.selectOne(new LambdaQueryWrapper<PosterEnrichmentJob>()
                .eq(PosterEnrichmentJob::getUserId, userId)
                .orderByDesc(PosterEnrichmentJob::getId).last("LIMIT 1"));
        return job == null ? null : view(job);
    }

    public PosterEnrichmentJobView get(long userId, long id) {
        PosterEnrichmentJob job = mapper.selectOne(new LambdaQueryWrapper<PosterEnrichmentJob>()
                .eq(PosterEnrichmentJob::getId, id).eq(PosterEnrichmentJob::getUserId, userId));
        if (job == null) throw new BusinessException(404, "海报补全任务不存在");
        return view(job);
    }

    public PageResult<PosterEnrichmentJobView> list(long userId, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(50, Math.max(1, size));
        IPage<PosterEnrichmentJob> jobs = mapper.selectPage(new Page<>(safePage, safeSize),
                new LambdaQueryWrapper<PosterEnrichmentJob>()
                        .eq(PosterEnrichmentJob::getUserId, userId)
                        .orderByDesc(PosterEnrichmentJob::getId));
        List<PosterEnrichmentJobView> records = jobs.getRecords().stream().map(this::view).toList();
        return new PageResult<>(records, jobs.getTotal(), jobs.getSize(), jobs.getCurrent(), jobs.getPages());
    }

    public PosterEnrichmentJobView cancel(long userId, long id) {
        PosterEnrichmentJob job = mapper.selectOne(new LambdaQueryWrapper<PosterEnrichmentJob>()
                .eq(PosterEnrichmentJob::getId, id).eq(PosterEnrichmentJob::getUserId, userId));
        if (job == null) throw new BusinessException(404, "海报补全任务不存在");
        if (!ACTIVE_STATUSES.contains(job.getStatus())) return view(job);
        job.setCancelRequested(1);
        job.setStatus("cancel_requested");
        job.setHeartbeatAt(now());
        mapper.updateById(job);
        return view(job);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void interruptStaleJobs() {
        LocalDateTime now = now();
        mapper.update(null, new LambdaUpdateWrapper<PosterEnrichmentJob>()
                .in(PosterEnrichmentJob::getStatus, ACTIVE_STATUSES)
                .set(PosterEnrichmentJob::getStatus, "interrupted")
                .set(PosterEnrichmentJob::getCancelRequested, 0)
                .set(PosterEnrichmentJob::getErrorSummary, "service_restarted")
                .set(PosterEnrichmentJob::getCurrentContentType, null)
                .set(PosterEnrichmentJob::getCurrentContentId, null)
                .set(PosterEnrichmentJob::getHeartbeatAt, now)
                .set(PosterEnrichmentJob::getFinishedAt, now));
    }

    private PosterEnrichmentJob active(long userId) {
        return mapper.selectOne(new LambdaQueryWrapper<PosterEnrichmentJob>()
                .eq(PosterEnrichmentJob::getUserId, userId)
                .in(PosterEnrichmentJob::getStatus, ACTIVE_STATUSES)
                .orderByDesc(PosterEnrichmentJob::getId).last("LIMIT 1"));
    }

    private PosterEnrichmentJobView view(PosterEnrichmentJob job) {
        return new PosterEnrichmentJobView(job.getId(), job.getStatus(), Integer.valueOf(1).equals(job.getCancelRequested()),
                job.getContentType(), value(job.getTotalCount()), value(job.getProcessedCount()),
                value(job.getMatchedCount()), value(job.getPendingCount()), value(job.getFailedCount()),
                job.getCurrentContentType(), job.getCurrentContentId(), job.getErrorSummary(), job.getQueuedAt(),
                job.getStartedAt(), job.getHeartbeatAt(), job.getFinishedAt());
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
