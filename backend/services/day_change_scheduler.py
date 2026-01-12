"""Day change scheduler for automatic task recalculation and notifications."""

import asyncio
import logging
from datetime import datetime, time, timedelta

from sqlalchemy.orm import Session

from backend.services.task_service import TaskService
from backend.services.time_manager import TimeManager
from backend.utils.date_utils import is_new_day


class DayChangeScheduler:
    """Scheduler that checks for day changes and performs automatic task recalculation."""

    def __init__(self, db_session_factory, ws_manager):
        """
        Initialize the scheduler.

        Args:
            db_session_factory: Factory function to create database sessions
            ws_manager: WebSocket connection manager for broadcasting notifications
        """
        self.db_factory = db_session_factory
        self.ws_manager = ws_manager
        self.logger = logging.getLogger("homeplanner.day_change_scheduler")
        self._running = False
        self._task = None

    async def check_and_notify(self):
        """Check for day change and perform recalculation with notification."""
        db = self.db_factory()
        try:
            self.logger.info("Checking for new day and performing task recalculation if needed")

            # Check for new day and recalculate tasks with notification if needed
            is_new_day = TaskService.check_new_day(db, self.ws_manager)

            if is_new_day:
                from backend.services.time_manager import TimeManager
                today = TimeManager.get_real_time().date().isoformat()
                self.logger.info(f"Day change processed: tasks recalculated and clients notified for {today}")
            else:
                self.logger.debug("No day change detected, skipping recalculation")
        except Exception as e:
            self.logger.error(f"Error in day change check: {e}", exc_info=True)
        finally:
            db.close()

    async def run(self):
        """Main scheduler loop - waits for next day change."""
        self.logger.info("DayChangeScheduler started - waiting for day changes")
        while self._running:
            try:
                await self._wait_for_next_day()
                if self._running:  # Check if still running after sleep
                    await self.check_and_notify()
            except Exception as e:
                self.logger.error(f"Error in scheduler loop: {e}", exc_info=True)
                # Wait a bit before retrying to avoid tight loops on errors
                await asyncio.sleep(60)

    async def _wait_for_next_day(self):
        """Wait until the start of the next day + 1 minute."""
        from backend.utils.date_utils import get_day_start
        from datetime import timedelta

        real_now = TimeManager.get_real_time()
        # Calculate start of next day + 1 minute
        next_day_start = get_day_start(real_now + timedelta(days=1))
        wait_until = next_day_start + timedelta(minutes=1)

        wait_seconds = (wait_until - real_now).total_seconds()
        if wait_seconds > 0:
            self.logger.debug(f"Waiting {wait_seconds:.0f} seconds until next day check at {wait_until}")
            await asyncio.sleep(wait_seconds)
        else:
            # If wait_seconds <= 0, it means we're already past the wait time, wait minimum 1 second
            await asyncio.sleep(1)

    async def start(self):
        """Start the scheduler."""
        if self._running:
            self.logger.warning("Scheduler already running")
            return

        self._running = True
        self._task = asyncio.create_task(self.run())
        self.logger.info("DayChangeScheduler task created")

    async def stop(self):
        """Stop the scheduler."""
        if not self._running:
            return

        self._running = False
        if self._task:
            self._task.cancel()
            try:
                await self._task
            except asyncio.CancelledError:
                pass
        self.logger.info("DayChangeScheduler stopped")