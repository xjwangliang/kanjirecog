/*
This file is part of leafdigital kanjirecog.

kanjirecog is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

kanjirecog is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with kanjirecog.  If not, see <http://www.gnu.org/licenses/>.

Copyright 2011 Samuel Marshall.
*/
package com.leafdigital.kanji;

import java.util.*;

/**
 * TODO Describe this
 * This algorithm is a lot faster than the fuzzy algorithm, but is currently
 * distinctly worse.
 */
public class SpansComparer implements KanjiComparer
{
	/**
	 * Number of categories for X and Y. Note: There is one array with (this
	 * number) to power four, so don't increase it too much...
	 */
	private final static int LOCATION_RANGE = 5;
	private final static int ARRAY_SIZE =
		LOCATION_RANGE * LOCATION_RANGE * LOCATION_RANGE * LOCATION_RANGE;

	private final static int SCORE_RIGHTDIRECTION = 2;
	private final static int SCORE_EXACTLOCATION = 4;
	private final static int SCORE_STRAIGHTLOCATION = 3;
	private final static int SCORE_DIAGONALLOCATION = 2;

	private final static int MAX_SCORE =
		SCORE_EXACTLOCATION * 2 + SCORE_RIGHTDIRECTION;
	private final static int MIN_SCORE = SCORE_DIAGONALLOCATION * 2;

	private final static int NO_MATCH = -1;

	/**
	 * Array of possible matching strokes. The indexes of this array are in
	 * the form startX * LOCATION_RANGE^3 + startY * LOCATION_RANGE^2 +
	 * endX * LOCATION_RANGE + endY
	 */
	private Position[] positions;

	private int count;

	private static class SpanScore implements Comparable<SpanScore>
	{
		private int stroke;
		private int score;

		/**
		 * @param stroke Index of stroke within drawn kanji
		 * @param score Score of this combination (how closely it reflects the stroke)
		 */
		SpanScore(int stroke, int score)
		{
			this.stroke = stroke;
			this.score = score;
		}

		@Override
		public int compareTo(SpanScore other)
		{
			if(other.score > score)
			{
				return 1;
			}
			else if(other.score < score)
			{
				return -1;
			}
			return other.stroke - stroke;
		}

		@Override
		public boolean equals(Object obj)
		{
			SpanScore other = (SpanScore)obj;
			return other.score == score && other.stroke == stroke;
		}

		@Override
		public int hashCode()
		{
			// Score never gets that high, just use bits to keep all of them
			return score + stroke * 1024;
		}
	}

	private static class Position
	{
		private SortedSet<SpanScore> spanSet = new TreeSet<SpanScore>();
		private SpanScore[] spanScores;

		void add(int stroke, int score)
		{
			spanSet.add(new SpanScore(stroke, score));
		}

		void finish()
		{
			spanScores = spanSet.toArray(new SpanScore[spanSet.size()]);
			spanSet = null;
		}

		/**
		 * Returns the id of the stroke that matches this position at this score
		 * or NO_MATCH if none
		 * @param minScore Required score
		 * @param used Array of used strokes
		 * @return Stroke index or NO_MATCH if nothing with that score
		 */
		int match(int minScore, boolean[] used)
		{
			for(SpanScore score : spanScores)
			{
				if(score.score < minScore)
				{
					return NO_MATCH;
				}
				if(!used[score.stroke])
				{
					return score.stroke;
				}
			}
			return NO_MATCH;
		}
	}

	/**
	 * Initialises with given drawn kanji.
	 * @param info Drawn kanji
	 */
	@Override
	public void init(KanjiInfo info)
	{
		// Create positions array
		positions = new Position[ARRAY_SIZE];
		for(int i=0; i<positions.length; i++)
		{
			positions[i] = new Position();
		}

		// Loop through all the strokes
		count = info.getStrokeCount();
		int[] startX = new int[count], startY = new int[count],
			endX = new int[count], endY = new int[count];
		getStrokePositions(info, startX, startY, endX, endY);
		for(int i=0; i<count; i++)
		{
			addSpan(i, startX[i], startY[i], endX[i], endY[i], true);
			addSpan(i, endX[i], endY[i], startX[i], startY[i], false);
		}

		// Finish everything
		for(int i=0; i<ARRAY_SIZE; i++)
		{
			positions[i].finish();
		}
	}

	/**
	 * Converts stroke positions into proportional positions (compared to all
	 * the other X, Y positions).
	 * @param info Kanji to convert
	 * @param startX Array to receive stroke start X data; will be filled with
	 *   values 0 .. LOCATION_RANGE-1
	 * @param startY Same for start Y
	 * @param endX Same for end X
	 * @param endY Same for end Y
	 */
	static void getStrokePositions(KanjiInfo info,
		int[] startX, int[] startY, int[] endX, int[] endY)
	{
		// Get all the stroke X, Y positions and sort them into order
		SortedSet<Integer> xPositions = new TreeSet<Integer>();
		SortedSet<Integer> yPositions = new TreeSet<Integer>();
		for(int i=0; i<info.getStrokeCount(); i++)
		{
			Stroke stroke = info.getStroke(i);
			xPositions.add(stroke.getStartX());
			xPositions.add(stroke.getEndX());
			yPositions.add(stroke.getStartY());
			yPositions.add(stroke.getEndY());
		}

		// Convert all into the LOCATION_RANGE positions
		Map<Integer, Integer> xConversion = new HashMap<Integer, Integer>();
		float eachTime = (float)LOCATION_RANGE / xPositions.size();
		float current = eachTime / 2;
		for(int x : xPositions)
		{
			xConversion.put(x, (int)current);
			current += eachTime;
		}
		Map<Integer, Integer> yConversion = new HashMap<Integer, Integer>();
		eachTime = (float)LOCATION_RANGE / yPositions.size();
		current = eachTime / 2;
		for(int y : yPositions)
		{
			yConversion.put(y, (int)current);
			current += eachTime;
		}

		// Now fill in the stroke arrays
		for(int i=0; i<info.getStrokeCount(); i++)
		{
			Stroke stroke = info.getStroke(i);
			startX[i] = xConversion.get(stroke.getStartX());
			startY[i] = yConversion.get(stroke.getStartY());
			endX[i] = xConversion.get(stroke.getEndX());
			endY[i] = yConversion.get(stroke.getEndY());
		}
	}

	private static int getIndex(int sX, int sY, int eX, int eY)
	{
		return sX * LOCATION_RANGE * LOCATION_RANGE * LOCATION_RANGE
			+ sY * LOCATION_RANGE * LOCATION_RANGE
			+ eX * LOCATION_RANGE
			+ eY;
	}

	private void addSpan(int stroke, int startX, int startY, int endX, int endY,
		boolean rightDirection)
	{
		for(int sX=startX-1; sX<=startX+1; sX++)
		{
			if(sX < 0 || sX >= LOCATION_RANGE)
			{
				continue;
			}
			for(int sY=startY-1; sY<=startY+1; sY++)
			{
				if(sY < 0 || sY >= LOCATION_RANGE)
				{
					continue;
				}
				for(int eX=endX-1; eX<=endX+1; eX++)
				{
					if(eX < 0 || eX >= LOCATION_RANGE)
					{
						continue;
					}
					for(int eY=endY-1; eY<=endY+1; eY++)
					{
						if(eY < 0 || eY >= LOCATION_RANGE)
						{
							continue;
						}

						// Get score
						int score;
						if(startX == sX && startY == sY)
						{
							score = SCORE_EXACTLOCATION;
						}
						else if(startX == sX || startY == sY)
						{
							score = SCORE_STRAIGHTLOCATION;
						}
						else
						{
							score = SCORE_DIAGONALLOCATION;
						}
						if(endX == eX && endY == eY)
						{
							score += SCORE_EXACTLOCATION;
						}
						else if(endX == eX || endY == eY)
						{
							score += SCORE_STRAIGHTLOCATION;
						}
						else
						{
							score += SCORE_DIAGONALLOCATION;
						}
						if(rightDirection)
						{
							score += SCORE_RIGHTDIRECTION;
						}

						// Add to positions
						positions[getIndex(sX, sY, eX, eY)].add(stroke, score);
					}
				}
			}
		}
	}


	/**
	 * Compares against the given other kanji.
	 * @param other Other kanji
	 * @return Score in range 0 to 100
	 */
	@Override
	public float getMatchScore(KanjiInfo other)
	{
		// Set up used array with nothing used
		boolean[] used = new boolean[count];
		int unmatched = count;

		// Convert each stroke ion the target kanji to a position index
		int otherCount = other.getStrokeCount();
		int otherUnmatched = otherCount;
		boolean[] otherUsed = new boolean[otherCount];
		int[] otherIndexes = new int[otherCount];
		int[] startX = new int[otherCount], startY = new int[otherCount],
			endX = new int[otherCount], endY = new int[otherCount];
		getStrokePositions(other, startX, startY, endX, endY);
		for(int i=0; i<otherCount; i++)
		{
			otherIndexes[i] = getIndex(startX[i], startY[i], endX[i], endY[i]);
		}

		// Calculate total score
		int score = 0;

		// Loop through all the strokes in the other kanji and try to match them
		// Begin with max score
		for(int requiredScore = MAX_SCORE; requiredScore >= MIN_SCORE; requiredScore--)
		{
			for(int i=0; i<otherCount; i++)
			{
				if(otherUsed[i])
				{
					continue;
				}

				int match = positions[otherIndexes[i]].match(requiredScore, used);
				if(match != NO_MATCH)
				{
					// Add score
					score += requiredScore;

					// Mark it as used
					otherUsed[i] = true;
					used[match] = true;
					unmatched--;
					otherUnmatched--;
					if(unmatched == 0 || otherUnmatched == 0)
					{
						break;
					}
				}
			}
		}

		// Work out as a proportion of max possible score
		int maxScore = Math.min(count, otherCount) * MAX_SCORE;
		return 100f * ((float)score / (float)maxScore);
	}
}
