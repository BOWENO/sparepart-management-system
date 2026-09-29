package com.sparepart.service;

import com.sparepart.entity.OperationLog;
import com.sparepart.mapper.OperationLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationLogWriter {
    private final OperationLogMapper mapper;
    public OperationLogWriter(OperationLogMapper mapper) { this.mapper = mapper; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(OperationLog log) { mapper.insert(log); }
}
