package com.et.cloud.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.et.cloud.mapper.WikiChunkMapper;
import com.et.cloud.model.entity.WikiChunk;
import com.et.cloud.service.WikiChunkService;
import org.springframework.stereotype.Service;

@Service
public class WikiChunkServiceImpl extends ServiceImpl<WikiChunkMapper, WikiChunk> implements WikiChunkService {
}
