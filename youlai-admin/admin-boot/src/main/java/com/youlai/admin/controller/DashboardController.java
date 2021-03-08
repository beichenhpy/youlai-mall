package com.youlai.admin.controller;

import cn.hutool.core.convert.Convert;
import com.youlai.admin.common.constant.ESConstants;
import com.youlai.common.elasticsearch.service.ElasticSearchService;
import com.youlai.common.result.Result;
import com.youlai.common.web.util.IpUtils;
import com.youlai.common.web.util.RequestUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * @author hxr
 * @description 首页控制台
 * @date 2021-03-08
 */
@Api(tags = "首页控制台")
@RestController
@RequestMapping("/api.admin/v1/dashboard")
@Slf4j
@AllArgsConstructor
public class DashboardController {

    ElasticSearchService elasticSearchService;

    @ApiOperation(value = "登录次数统计", httpMethod = "GET")
    @GetMapping("/login_counts")
    public Result loginCounts() {
        int days = 10; //统计天数
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        String startDate = now.plusDays(-days).format(formatter);
        String endDate = now.format(formatter);

        String[] indices = new String[days]; // 查询ES索引数组
        String[] xData = new String[days]; // 柱状图x轴数据
        for (int i = 0; i < days; i++) {
            String date = now.plusDays(-i).format(formatter);
            xData[i] = date;
            indices[i] = ESConstants.INDEX_LOGIN_PREFIX + date;
        }

        // 查询条件，范围内日期统计
        RangeQueryBuilder rangeQueryBuilder = QueryBuilders.rangeQuery("date").from(startDate).to(endDate);
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery()
                .must(rangeQueryBuilder)
                /*.must(QueryBuilders.wildcardQuery("accessToken", "*"))*/; // 登录成功统计

        // 总数统计
        Map<String, Long> totalCountMap = elasticSearchService.dateHistogram(
                        boolQueryBuilder,
                        "date", // 根据date字段聚合统计登录数 logback-spring.xml 中的自定义扩展字段 date
                        DateHistogramInterval.days(1),
                        indices);

        // 当前用户统计
        HttpServletRequest request = RequestUtils.getRequest();
        String clientIP = IpUtils.getIpAddr(request);

        boolQueryBuilder.must(QueryBuilders.termQuery("clientIP", clientIP));
        Map<String, Long> myCountMap = elasticSearchService.dateHistogram(boolQueryBuilder, "date", DateHistogramInterval.days(1), indices);


        // 组装echarts数据
        Long[] totalCount = new Long[days];
        Long[] myCount= new Long[days];

        Arrays.sort(xData);// 默认升序排序
        for (int i = 0; i < days; i++) {
            String key = xData[i];
            totalCount[i] = Convert.toLong(totalCountMap.get(key), 0l);
            myCount[i] = Convert.toLong(myCountMap.get(key), 0l);
        }
        Map<String, Object> map = new HashMap<>(4);

        map.put("xData", xData); // x轴坐标
        map.put("totalCount", totalCount); // 总数
        map.put("myCount", myCount); // 我的

        return Result.success(map);
    }

}