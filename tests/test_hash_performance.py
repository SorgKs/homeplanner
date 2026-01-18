"""Test hash performance for large datasets."""

import time
import pytest
from unittest.mock import Mock
from datetime import datetime, timezone

from backend.utils.hash_calculator import HashCalculator


class TestHashPerformance:
    """Test hash calculation performance."""

    def create_mock_task(self, task_id: int) -> Mock:
        """Create a mock task for testing."""
        task = Mock()
        task.id = task_id
        task.title = f"Task {task_id}"
        task.description = f"Description for task {task_id}"
        task.task_type = "one_time"
        task.recurrence_type = None
        task.recurrence_interval = None
        task.interval_days = None
        task.reminder_time = datetime(2024, 1, 1, 10, 0, 0, tzinfo=timezone.utc)
        task.group_id = None
        task.enabled = True
        task.completed = False
        task.assigned_user_ids = [1, 2, 3]
        task.updated_at = datetime(2024, 1, 1, 12, 0, 0, tzinfo=timezone.utc)
        return task

    def test_single_task_hash_performance(self):
        """Test performance of single task hash calculation."""
        task = self.create_mock_task(1)

        # Measure time for single hash
        start_time = time.time()
        for _ in range(1000):
            hash_value = HashCalculator.calculate_task_hash(task)
        end_time = time.time()

        total_time = end_time - start_time
        avg_time = total_time / 1000

        print(f"Single task hash: 1000 iterations took {total_time:.4f}s, avg {avg_time:.6f}s per hash")
        assert avg_time < 0.001  # Should be less than 1ms per hash

    def test_bulk_task_hash_performance(self):
        """Test performance of bulk task hash calculation."""
        tasks = [self.create_mock_task(i) for i in range(100)]

        # Measure time for bulk hash calculation
        start_time = time.time()
        hashes = []
        for task in tasks:
            hash_value = HashCalculator.calculate_task_hash(task)
            hashes.append(hash_value)
        end_time = time.time()

        total_time = end_time - start_time
        avg_time = total_time / len(tasks)

        print(f"Bulk task hash: {len(tasks)} tasks took {total_time:.4f}s, avg {avg_time:.6f}s per hash")
        assert total_time < 0.1  # Should process 100 tasks in less than 100ms
        assert len(hashes) == len(tasks)
        assert len(set(hashes)) == len(tasks)  # All hashes should be unique

    def test_combined_hash_performance(self):
        """Test performance of combined hash calculation."""
        # Create 100 id-hash pairs
        id_hashes = [(i, f"hash_value_{i}") for i in range(100)]

        # Measure time for combined hash
        start_time = time.time()
        for _ in range(100):
            combined_hash = HashCalculator.calculate_combined_hash(id_hashes)
        end_time = time.time()

        total_time = end_time - start_time
        avg_time = total_time / 100

        print(f"Combined hash: 100 iterations took {total_time:.4f}s, avg {avg_time:.6f}s per combined hash")
        assert avg_time < 0.001  # Should be less than 1ms per combined hash

    def test_large_dataset_performance(self):
        """Test performance with large dataset (1000 tasks)."""
        tasks = [self.create_mock_task(i) for i in range(1000)]

        start_time = time.time()
        hashes = []
        for task in tasks:
            hash_value = HashCalculator.calculate_task_hash(task)
            hashes.append(hash_value)
        end_time = time.time()

        total_time = end_time - start_time
        avg_time = total_time / len(tasks)

        print(f"Large dataset: {len(tasks)} tasks took {total_time:.4f}s, avg {avg_time:.6f}s per hash")
        print(f"That's {len(tasks)/total_time:.0f} hashes per second")

        # Performance requirements: should handle 1000 tasks in reasonable time
        assert total_time < 1.0  # Should process 1000 tasks in less than 1 second
        assert len(hashes) == len(tasks)
        assert len(set(hashes)) == len(tasks)  # All hashes should be unique

    def test_memory_efficiency(self):
        """Test that hash calculation doesn't cause memory issues."""
        import psutil
        import os

        # Get initial memory
        process = psutil.Process(os.getpid())
        initial_memory = process.memory_info().rss / 1024 / 1024  # MB

        # Create and hash many tasks
        tasks = [self.create_mock_task(i) for i in range(10000)]

        start_time = time.time()
        hashes = []
        for task in tasks:
            hash_value = HashCalculator.calculate_task_hash(task)
            hashes.append(hash_value)
        end_time = time.time()

        # Check memory after
        final_memory = process.memory_info().rss / 1024 / 1024  # MB
        memory_increase = final_memory - initial_memory

        total_time = end_time - start_time
        avg_time = total_time / len(tasks)

        print(f"Memory test: {len(tasks)} tasks, memory increase: {memory_increase:.2f} MB")
        print(f"Time: {total_time:.4f}s, avg {avg_time:.6f}s per hash")

        # Memory increase should be reasonable (less than 100MB for 10k tasks in Python)
        assert memory_increase < 100.0
        assert len(hashes) == len(tasks)