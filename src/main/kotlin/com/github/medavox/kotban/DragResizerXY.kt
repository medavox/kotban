package com.github.medavox.kotban

import javafx.event.EventHandler
import javafx.scene.Cursor
import javafx.scene.input.MouseEvent
import javafx.scene.layout.Region

/**
 * The margin around the control that a user can click in to start resizing
 * the region.
 */
private const val RESIZE_MARGIN = 10

/**
 * {@link DragResizerXY} can be used to add mouse listeners to a {@link Region}
 * and make it resizable by the user by clicking and dragging the border in the
 * same way as a window.
 * <p>
 * Height and Width resizing is working (hopefully) properly
 * 
 * <pre>
 * DragResizer.makeResizable(myAnchorPane)
 * </pre>
 *
 * @author Cannibalsticky (modified from the original DragResizer created by AndyTill)
 *
 */
class DragResizerXY(private val region:Region) {

	private var y:Double=0.0
	private var x:Double=0.0
	private var initMinHeight:Boolean=false
	private var initMinWidth:Boolean=false
	private var draggableZoneX:Boolean=false
	private var draggableZoneY:Boolean=false
	private var dragging:Boolean=false

	fun makeResizable() {
		region.setOnMousePressed { event ->
			// ignore clicks outside of the draggable margin
			if (!isInDraggableZone(event)) { Unit }

			dragging = true

			// make sure that the minimum height is set to the current height once,
			// setting a min height that is smaller than the current height will
			// have no effect
			if (!initMinHeight) {
				region.minHeight = region.height
				initMinHeight = true
			}

			y = event.y

			if (!initMinWidth) {
				region.minWidth = region.width
				initMinWidth = true
			}

			x = event.x
		}
		region.setOnMouseDragged { event ->
			if (!dragging) { Unit }

			if (draggableZoneY) {
				region.minHeight = region.minHeight + (event.y - y)
				y = event.y
			}

			if (draggableZoneX) {
				region.minWidth = region.minWidth + (event.x - x)
				x = event.x
			}
		}
		region.setOnMouseMoved { event ->
			if (isInDraggableZone(event) || dragging) {
				if (draggableZoneY) { region.cursor = Cursor.S_RESIZE }

				if (draggableZoneX) { region.cursor = Cursor.E_RESIZE }

			} else { region.cursor = Cursor.DEFAULT }
		}
		region.setOnMouseReleased {
			dragging = false
			region.cursor = Cursor.DEFAULT
		}
	}

	/**Had to use 2 variables for the control.
	Tried without, had unexpected behaviour (going big was ok, going small nope.)*/
	private fun isInDraggableZone(event:MouseEvent):Boolean {
		draggableZoneY = (event.y > (region.height - RESIZE_MARGIN))
		draggableZoneX = (event.x > (region.width - RESIZE_MARGIN))
		return (draggableZoneY || draggableZoneX)
	}
}
