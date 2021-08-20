package com.itcall.util.PagingUtil;

import java.util.ArrayList;

public class PagingUtil {
	
	public int PAGE_SIZE = 10;
	public int BLOCK_SIZE = 10;

	private int totalCount;
	private int currentPageNum;

	public PagingUtil(int totalCount, int currentPageNum, int pageSize, int blockSize) {
		this.totalCount = totalCount;
		this.currentPageNum = currentPageNum;
		this.PAGE_SIZE = pageSize;
		this.BLOCK_SIZE = blockSize;
	}

	public int getTotalCount() {
		return totalCount;
	}

	public int getCurrentPageNum() {
		return currentPageNum;
	}

	public int getTotalPages() {
		return (int) Math.ceil((double) totalCount / PAGE_SIZE);
	}

	public int getStartPageNumForBoard() {
		return getStartPageNum() - 1 <= 0 ? 0 : getStartPageNum() - 1;
	}

	public int getStartPageNum() {
		return totalCount == 0 ? 0 : ((currentPageNum - 1) * PAGE_SIZE) + 1;
	}

	public int getEndPageNum() {
		return (getStartPageNum() + PAGE_SIZE - 1) > totalCount ? totalCount : (getStartPageNum() + PAGE_SIZE - 1);
	}

	public int getPrePageNum() {
		return (currentPageNum - 1) <= 0 ? 1 : (currentPageNum - 1);
	}

	public int getNextPageNum() {
		return (currentPageNum + 1) > getTotalPages() ? getTotalPages() : (currentPageNum + 1);
	}

	public int getRealStartPageNum() {
		return (totalCount - ((currentPageNum - 1) * PAGE_SIZE));
	}

	public int getRealEndPageNum() {
		return getRealStartPageNum() - PAGE_SIZE + 1 < 0 ? 1 : getRealStartPageNum() - PAGE_SIZE + 1;
	}

	public int getPreviousBlock() {
		return getStartBlock() - 1 <= 1 ? 1 : getStartBlock() - 1;
	}

	public int getNextBlock() {
		return getEndBlock() + 1 > getTotalPages() ? getTotalPages() : getEndBlock() + 1;
	}

	public int getCurrentBlock() {
		return (int) Math.ceil((double) ((getCurrentPageNum() - 1) / BLOCK_SIZE)) + 1;
	}

	public int getStartBlock() {
		return ((getCurrentBlock() - 1) * BLOCK_SIZE) + 1;
	}

	public int getEndBlock() {
		return (getStartBlock() + BLOCK_SIZE - 1) > getTotalPages() ? getTotalPages()
				: (getStartBlock() + BLOCK_SIZE - 1);
	}

	public ArrayList<Integer> getPages() {
		ArrayList<Integer> result = new ArrayList<Integer>();
		for (int i = getStartBlock(); i <= getEndBlock(); i++) {
			result.add(i);
		}
		return result;
	}
}
