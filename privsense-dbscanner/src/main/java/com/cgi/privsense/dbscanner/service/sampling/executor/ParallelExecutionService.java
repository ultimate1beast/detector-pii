package com.cgi.privsense.dbscanner.service.sampling.executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Service for executing tasks in parallel.
 * Manages thread pools and execution strategies.
 */
public class ParallelExecutionService implements DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(ParallelExecutionService.class);
    
    private final ExecutorService executorService;
    private final int maxConcurrentTasks;
    private final long defaultTimeout;
    private final TimeUnit timeoutUnit;
    
    /**
     * Creates a new parallel execution service.
     *
     * @param maxThreads Maximum number of threads
     * @param defaultTimeout Default timeout for operations
     * @param timeoutUnit Timeout unit
     */
    public ParallelExecutionService(int maxThreads, long defaultTimeout, TimeUnit timeoutUnit) {
        this.maxConcurrentTasks = maxThreads;
        this.defaultTimeout = defaultTimeout;
        this.timeoutUnit = timeoutUnit;
        
        // Create a thread pool with virtual threads for efficiency
        this.executorService = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("sampler-", 0).factory());
        
        log.info("Initialized parallel execution service with {} max threads", maxThreads);
    }
    
    /**
     * Executes multiple tasks in parallel and collects their results.
     *
     * @param <T> Input type
     * @param <R> Result type
     * @param inputs Collection of inputs
     * @param taskFunction Function to convert an input to a CompletableFuture
     * @return List of results
     */
    public <T, R> List<R> executeParallel(Collection<T> inputs, 
                                        Function<T, CompletableFuture<R>> taskFunction) {
        return executeParallel(inputs, taskFunction, defaultTimeout, timeoutUnit);
    }
    
    /**
     * Executes multiple tasks in parallel and collects their results.
     *
     * @param <T> Input type
     * @param <R> Result type
     * @param inputs Collection of inputs
     * @param taskFunction Function to convert an input to a CompletableFuture
     * @param timeout Timeout value
     * @param unit Timeout unit
     * @return List of results
     */
    public <T, R> List<R> executeParallel(Collection<T> inputs, 
                                        Function<T, CompletableFuture<R>> taskFunction,
                                        long timeout, TimeUnit unit) {
        // Limit concurrent tasks
        int concurrentTasks = Math.min(inputs.size(), maxConcurrentTasks);
        Semaphore semaphore = new Semaphore(concurrentTasks);
        
        // Create futures
        List<CompletableFuture<R>> futures = new ArrayList<>(inputs.size());
        for (T input : inputs) {
            CompletableFuture<R> future = CompletableFuture.supplyAsync(() -> {
                try {
                    semaphore.acquire(); // Limit concurrent executions
                    return taskFunction.apply(input).get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Task was interrupted");
                    throw new CompletionException(e);
                } catch (ExecutionException e) {
                    
                    throw new CompletionException(e.getCause());
                } finally {
                    semaphore.release();
                }
            }, executorService);
            
            futures.add(future);
        }
        
        // Wait for all futures to complete
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0]));
        
        try {
            allFutures.get(timeout, unit);
        } catch (TimeoutException e) {
            log.warn("Timeout waiting for tasks to complete");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Tasks were interrupted");
        } catch (Exception e) {
            log.error("Error waiting for tasks: {}", e.getMessage());
        }
        
        // Collect results
        List<R> results = new ArrayList<>(inputs.size());
        for (CompletableFuture<R> future : futures) {
            try {
                R result = future.getNow(null);
                if (result != null) {
                    results.add(result);
                }
            } catch (Exception e) {
                log.error("Error getting task result: {}", e.getMessage());
            }
        }
        
        return results;
    }
    
    /**
     * Executes a task with limiting concurrent executions.
     *
     * @param <R> Result type
     * @param task Task to execute
     * @param semaphore Semaphore for limiting concurrent executions
     * @return CompletableFuture with the result
     */
    public <R> CompletableFuture<R> executeWithSemaphore(Supplier<R> task, Semaphore semaphore) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                semaphore.acquire(); // Limit concurrent executions
                return task.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CompletionException(e);
            } finally {
                semaphore.release();
            }
        }, executorService);
    }
    
    /**
     * Executes multiple keyed tasks in parallel and collects their results in a map.
     *
     * @param <K> Key type
     * @param <V> Value type
     * @param inputs Map of inputs
     * @param taskFunction Function to convert a key-value pair to a CompletableFuture
     * @return Map of results
     */
    public <K, V> Map<K, V> executeParallelMap(Map<K, ?> inputs, 
                                             Function<Map.Entry<K, ?>, CompletableFuture<V>> taskFunction) {
        return executeParallelMap(inputs, taskFunction, defaultTimeout, timeoutUnit);
    }
    
    /**
     * Executes multiple keyed tasks in parallel and collects their results in a map.
     *
     * @param <K> Key type
     * @param <V> Value type
     * @param inputs Map of inputs
     * @param taskFunction Function to convert a key-value pair to a CompletableFuture
     * @param timeout Timeout value
     * @param unit Timeout unit
     * @return Map of results
     */
    public <K, V> Map<K, V> executeParallelMap(Map<K, ?> inputs, 
                                             Function<Map.Entry<K, ?>, CompletableFuture<V>> taskFunction,
                                             long timeout, TimeUnit unit) {
        // Limit concurrent tasks
        int concurrentTasks = Math.min(inputs.size(), maxConcurrentTasks);
        Semaphore semaphore = new Semaphore(concurrentTasks);
        
        // Create futures
        Map<K, CompletableFuture<V>> futures = new ConcurrentHashMap<>(inputs.size());
        for (Map.Entry<K, ?> entry : inputs.entrySet()) {
            K key = entry.getKey();
            CompletableFuture<V> future = CompletableFuture.supplyAsync(() -> {
                try {
                    semaphore.acquire(); // Limit concurrent executions
                    return taskFunction.apply(entry).get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Task for key {} was interrupted", key);
                    throw new CompletionException(e);
                } catch (ExecutionException e) {
                   
                    throw new CompletionException(e.getCause());
                } finally {
                    semaphore.release();
                }
            }, executorService);
            
            futures.put(key, future);
        }
        
        // Wait for all futures to complete
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futures.values().toArray(new CompletableFuture[0]));
        
        try {
            allFutures.get(timeout, unit);
        } catch (TimeoutException e) {
            log.warn("Timeout waiting for tasks to complete");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Tasks were interrupted");
        } catch (Exception e) {
            log.error("Error waiting for tasks: {}", e.getMessage());
        }
        
        // Collect results
        Map<K, V> results = new ConcurrentHashMap<>(inputs.size());
        futures.forEach((key, future) -> {
            try {
                V result = future.getNow(null);
                if (result != null) {
                    results.put(key, result);
                }
            } catch (Exception e) {
                log.error("Error getting task result for key {}: {}", key, e.getMessage());
            }
        });
        
        return results;
    }
    
    /**
     * Shuts down the executor service.
     */
    public void shutdown() {
        log.info("Shutting down parallel execution service");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executorService.shutdownNow();
        }
    }
    
    /**
     * Disposes the executor service.
     * Called by Spring when the bean is destroyed.
     */
    @Override
    public void destroy() {
        shutdown();
    }
}